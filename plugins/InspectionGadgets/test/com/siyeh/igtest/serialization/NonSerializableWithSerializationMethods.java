package com.siyeh.igtest.serialization;

import java.io.*;

public class NonSerializableWithSerializationMethods
{
    private static final long serialVersionUID = 1;

    private void readObject(ObjectInputStream str)
    {

    }

    private void writeObject(ObjectOutputStream str)
    {

    }
}
