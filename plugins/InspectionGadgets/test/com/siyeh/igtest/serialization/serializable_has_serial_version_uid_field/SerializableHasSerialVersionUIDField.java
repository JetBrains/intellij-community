package com.siyeh.igtest.serialization.serializable_has_serial_version_uid_field;

import java.io.Serializable;

public class <warning descr="'SerializableHasSerialVersionUIDField' does not define a 'serialVersionUID' field">SerializableHasSerialVersionUIDField</warning><T extends Serializable> implements Serializable {


    public abstract class <warning descr="'DoWarnOnMe' does not define a 'serialVersionUID' field">DoWarnOnMe</warning> implements Serializable { }

    public interface X extends Serializable {}
}

record R() implements Serializable {
}