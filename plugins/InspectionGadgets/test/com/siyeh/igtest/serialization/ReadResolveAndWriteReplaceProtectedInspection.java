package com.siyeh.igtest.serialization;

import java.io.*;

public class ReadResolveAndWriteReplaceProtectedInspection implements Serializable
{
    private static final long serialVersionUID = 1;

    public ReadResolveAndWriteReplaceProtectedInspection()
    {
    }

    private void readObject(ObjectInputStream str)
    {

    }

    private void writeObject(ObjectOutputStream str)
    {

    }

    Object writeReplace() throws ObjectStreamException
    {
        return null;
    }

    public Object readResolve() throws ObjectStreamException
    {
        return null;
    }
}

