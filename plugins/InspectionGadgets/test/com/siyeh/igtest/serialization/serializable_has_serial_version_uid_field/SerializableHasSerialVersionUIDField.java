package com.siyeh.igtest.serialization.serializable_has_serial_version_uid_field;

import java.io.Serializable;

public class SerializableHasSerialVersionUIDField<T extends Serializable> implements Serializable {


    public abstract class DoWarnOnMe implements Serializable { }

    public interface X extends Serializable {}
}