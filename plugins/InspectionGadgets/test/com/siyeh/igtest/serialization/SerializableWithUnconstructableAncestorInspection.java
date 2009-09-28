package com.siyeh.igtest.serialization;


import java.io.*;

public class SerializableWithUnconstructableAncestorInspection  extends SerializableParent implements Serializable
{
    public SerializableWithUnconstructableAncestorInspection(int arg, int foo)
    {
        super(arg, foo);
    }

}

