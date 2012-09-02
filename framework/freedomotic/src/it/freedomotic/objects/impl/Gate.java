package it.freedomotic.objects.impl;

import it.freedomotic.app.Freedomotic;
import it.freedomotic.core.EnvObjectLogic;
import it.freedomotic.environment.Room;
import it.freedomotic.environment.ZoneLogic;
import it.freedomotic.model.ds.Config;
import it.freedomotic.model.geometry.FreedomShape;
import it.freedomotic.model.object.BooleanBehavior;
import it.freedomotic.model.object.RangedIntBehavior;
import it.freedomotic.model.object.Representation;
import it.freedomotic.objects.BooleanBehaviorLogic;
import it.freedomotic.objects.RangedIntBehaviorLogic;
import it.freedomotic.reactions.CommandPersistence;
import it.freedomotic.reactions.TriggerPersistence;
import it.freedomotic.reactions.Command;
import it.freedomotic.reactions.Trigger;
import it.freedomotic.util.AWTConverter;
import java.awt.Shape;
import java.awt.geom.AffineTransform;

/**
 *
 * @author Enrico
 */
public class Gate extends EnvObjectLogic {
    //suppose from and to are always reflexive from->to; to->from

    private Room from;
    private Room to;
    protected RangedIntBehaviorLogic openness;
    protected BooleanBehaviorLogic open;

    @Override
    public void init() {
        super.init();
        //linking this open property with the open behavior defined in the XML
        open = new BooleanBehaviorLogic((BooleanBehavior) getPojo().getBehaviors().get(0));
//        open.createCommands(this);
        open.addListener(new BooleanBehaviorLogic.Listener() {
            @Override
            public void onTrue(Config params, boolean fireCommand) {
                //open = true
                setOpen(params);
            }

            @Override
            public void onFalse(Config params, boolean fireCommand) {
                //open = false -> not open
                setClosed(params);
            }
        });

        //linking this property with the behavior defined in the XML
        openness = new RangedIntBehaviorLogic((RangedIntBehavior) getPojo().getBehaviors().get(1));
//        openness.createCommands(this);
        openness.addListener(new RangedIntBehaviorLogic.Listener() {
            @Override
            public void onLowerBoundValue(Config params, boolean fireCommand) {
                //on value = 0
                setClosed(params);
            }

            @Override
            public void onUpperBoundValue(Config params, boolean fireCommand) {
                //on value = 100
                setOpen(params);
            }

            @Override
            public void onRangeValue(int rangeValue, Config params, boolean fireCommand) {
                //on values between 1 to 99
                setOpeness(rangeValue, params);
            }
        });
        //register this behavior to the superclass to make it visible to it
        registerBehavior(open);
        registerBehavior(openness);
        getPojo().setDescription("Connects no rooms");
        //evaluate witch rooms it connects (based on gate position)
        //the evaluation updates the gate description
        evaluateGate();
    }

    protected void setClosed(Config params) {
        boolean executed = executeCommand("close", params); //executes the developer level command associated with 'set brightness' action
        if (executed) {
            open.setValue(false);
            //to mantain the object coerence
            openness.setValue(0);
            //set the light graphical representation
            getPojo().setCurrentRepresentation(0); //points to the first element in the XML views array (closed door)
            setChanged(true);
        }
    }

    protected void setOpen(Config params) {
        boolean executed = executeCommand("open", params); //executes the developer level command associated with 'set brightness' action
        if (executed) {
            open.setValue(true);
            //to mantain the object coerence
            openness.setValue(100);
            //set the light graphical representation
            getPojo().setCurrentRepresentation(1); //points to the second element in the XML views array (open door)
            setChanged(true);
        }
    }

    protected void setOpeness(int rangeValue, Config params) {
        boolean executed = executeCommand("measured open", params); //executes the developer level command associated with 'set brightness' action
        if (executed) {
            //here we never had 0 or 100
            open.setValue(true);
            //to mantain the object coerence
            openness.setValue(rangeValue);
            //set the light graphical representation
            getPojo().setCurrentRepresentation(2); //points to the second element in the XML views array (half open door)
            setChanged(true);
        }
    }

    @Override
    public final void setChanged(final boolean isChanged) {
        //first update the object
        if (isChanged) {
            //update the room that can be reached
            for (ZoneLogic z : Freedomotic.environment.getZones()) {
                if (z instanceof Room) {
                    final Room room = (Room) z;
                    //the gate is opened or closed we update the reachable rooms
                    room.visit();
                }
            }
            for (ZoneLogic z : Freedomotic.environment.getZones()) {
                if (z instanceof Room) {
                    final Room room = (Room) z;
                    room.updateDescription();
                }
            }
        }
        //then executeCommand the super which notifies the event
        super.setChanged(isChanged);
    }

    public boolean isOpen() {
        return open.getValue();
    }

    public Room getFrom() {
        return from;
    }

    public Room getTo() {
        return to;
    }

    public void evaluateGate() {
        //checks the intersection with the first view in the list
        //others views are ignored!!!
        Representation representation = getPojo().getRepresentations().get(0);
        FreedomShape pojoShape = representation.getShape();
        int xoffset = representation.getOffset().getX();
        int yoffset = representation.getOffset().getY();
        double rotation = Math.toRadians(representation.getRotation());
        from = null;
        to = null;
        //now apply offset and rotation to gate the shape
        AffineTransform transform = new AffineTransform();
        transform.translate(xoffset, yoffset);
        transform.rotate(rotation);
        Shape gateShape = transform.createTransformedShape(AWTConverter.convertToAWT(pojoShape));

        for (Room room : Freedomotic.environment.getRooms()) {
            Shape roomPolygon = AWTConverter.convertToAWT(room.getPojo().getShape());
            if (roomPolygon.intersects(gateShape.getBounds2D())) {
                if (from == null) {
                    from = (Room) room;
                    to = (Room) room;
                } else {
                    to = (Room) room;
                }
            }
        }
        if (to != from) {
            getPojo().setDescription("Connects " + from + " to " + to);
            from.addGate(this); //informs the room that it has a gate to another room
            to.addGate(this); //informs the room that it has a gate to another room
        } else {
            //the gate interects two equals zones
            if (from != null) {
                Freedomotic.logger.warning("The gate '" + getPojo().getName() + "' connects the same zones ["
                        + from.getPojo().getName() + "; "
                        + to.getPojo().getName() + "]. This is not possible.");
            }
        }
        //notify if the passage connect two rooms
        Freedomotic.logger.info("The gate '" + getPojo().getName() + "' connects " + from + " to " + to);
    }

    @Override
    protected void createCommands() {
        Command a = new Command();
        a.setName("Set " + getPojo().getName() + " openness to 50%");
        a.setDescription("the " + getPojo().getName() + " changes its openness");
        a.setReceiver("app.events.sensors.behavior.request.objects");
        a.setProperty("object", getPojo().getName());
        a.setProperty("behavior", "openness");
        a.setProperty("value", "50");

        Command b = new Command();
        b.setName("Increase " + getPojo().getName() + " openness");
        b.setDescription("increases " + getPojo().getName() + " openness of one step");
        b.setReceiver("app.events.sensors.behavior.request.objects");
        b.setProperty("object", getPojo().getName());
        b.setProperty("behavior", "openness");
        b.setProperty("value", "next");

        Command c = new Command();
        c.setName("Decrease " + getPojo().getName() + " openness");
        c.setDescription("decreases " + getPojo().getName() + " openness of one step");
        c.setReceiver("app.events.sensors.behavior.request.objects");
        c.setProperty("object", getPojo().getName());
        c.setProperty("behavior", "openness");
        c.setProperty("value", "previous");

        Command d = new Command();
        d.setName("Set its openness to 50%");
        d.setDescription("set its openness to 50%");
        d.setReceiver("app.events.sensors.behavior.request.objects");
        d.setProperty("object", "@event.object.name");
        d.setProperty("behavior", "openness");
        d.setProperty("value", "50");

        Command e = new Command();
        e.setName("Increase its openness");
        e.setDescription("increases its openness of one step");
        e.setReceiver("app.events.sensors.behavior.request.objects");
        e.setProperty("object", "@event.object.name");
        e.setProperty("behavior", "openness");
        e.setProperty("value", "next");

        Command f = new Command();
        f.setName("Decrease its openness");
        f.setDescription("decreases its openness of one step");
        f.setReceiver("app.events.sensors.behavior.request.objects");
        f.setProperty("object", "@event.object.name");
        f.setProperty("behavior", "openness");
        f.setProperty("value", "previous");


        Command g = new Command();
        g.setName("Set its openness to the value in the event");
        g.setDescription("set its openness to the value in the event");
        g.setReceiver("app.events.sensors.behavior.request.objects");
        g.setProperty("object", "@event.object.name");
        g.setProperty("behavior", "openness");
        g.setProperty("value", "@event.value");

        Command h = new Command();
        h.setName("Open " + getPojo().getName());
        h.setDescription(getPojo().getSimpleType() + " opens");
        h.setReceiver("app.events.sensors.behavior.request.objects");
        h.setProperty("object", getPojo().getName());
        h.setProperty("behavior", "open");
        h.setProperty("value", "true");

        Command i = new Command();
        i.setName("Close " + getPojo().getName());
        i.setDescription(getPojo().getSimpleType() + " closes");
        i.setReceiver("app.events.sensors.behavior.request.objects");
        i.setProperty("object", getPojo().getName());
        i.setProperty("behavior", "open");
        i.setProperty("value", "false");

        Command l = new Command();
        l.setName("Switch " + getPojo().getName() + " open state");
        l.setDescription("closes/opens " + getPojo().getName());
        l.setReceiver("app.events.sensors.behavior.request.objects");
        l.setProperty("object", getPojo().getName());
        l.setProperty("behavior", "open");
        l.setProperty("value", "opposite");

        Command m = new Command();
        m.setName("Open this gate");
        m.setDescription("this gate is opened");
        m.setReceiver("app.events.sensors.behavior.request.objects");
        m.setProperty("object", "@event.object.name");
        m.setProperty("behavior", "open");
        m.setProperty("value", "true");

        Command n = new Command();
        n.setName("Close this gate");
        n.setDescription("this gate is closed");
        n.setReceiver("app.events.sensors.behavior.request.objects");
        n.setProperty("object", "@event.object.name");
        n.setProperty("behavior", "open");
        n.setProperty("value", "false");

        Command o = new Command();
        o.setName("Switch its open state");
        o.setDescription("opens/closes the gate in the event");
        o.setReceiver("app.events.sensors.behavior.request.objects");
        o.setProperty("object", "@event.object.name");
        o.setProperty("behavior", "open");
        o.setProperty("value", "opposite");


        CommandPersistence.add(a);
        CommandPersistence.add(b);
        CommandPersistence.add(c);
        CommandPersistence.add(d);
        CommandPersistence.add(e);
        CommandPersistence.add(f);
        CommandPersistence.add(g);
        CommandPersistence.add(h);
        CommandPersistence.add(i);
        CommandPersistence.add(l);
        CommandPersistence.add(m);
        CommandPersistence.add(n);
        CommandPersistence.add(o);

    }

    @Override
    protected void createTriggers() {
        Trigger clicked = new Trigger();
        clicked.setName("When " + this.getPojo().getName() + " is clicked");
        clicked.setChannel("app.event.sensor.object.behavior.clicked");
        clicked.getPayload().addStatement("object.name", this.getPojo().getName());
        clicked.getPayload().addStatement("click", "SINGLE_CLICK");

        Trigger turnsOpen = new Trigger();
        turnsOpen.setName(this.getPojo().getName() + " becomes open");
        turnsOpen.setChannel("app.event.sensor.object.behavior.change");
        turnsOpen.getPayload().addStatement("object.name", this.getPojo().getName());
        turnsOpen.getPayload().addStatement("open", "true");

        Trigger turnsClosed = new Trigger();
        turnsClosed.setName(this.getPojo().getName() + " becomes closed");
        turnsClosed.setChannel("app.event.sensor.object.behavior.change");
        turnsClosed.getPayload().addStatement("object.name", this.getPojo().getName());
        turnsClosed.getPayload().addStatement("open", "false");

        TriggerPersistence.add(clicked);
        TriggerPersistence.add(turnsOpen);
        TriggerPersistence.add(turnsClosed);
    }
}
