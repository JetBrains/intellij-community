package com.siyeh.igtest.serialization;

import java.io.Serializable;

public class SerializableParent
        extends NonserializableGrandarent implements Serializable
{
    private int m_foo;

    public SerializableParent(int arg, int foo)
    {
        super(arg);
        m_foo = foo;
    }
}
