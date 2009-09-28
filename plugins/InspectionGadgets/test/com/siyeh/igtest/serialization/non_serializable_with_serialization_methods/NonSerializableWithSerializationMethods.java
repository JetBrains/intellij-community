package com.siyeh.igtest.serialization.non_serializable_with_serialization_methods;



public class NonSerializableWithSerializationMethods
{
    private static final long serialVersionUID = 1;

    private void readObject(java.io.ObjectInputStream str)
    {

    }

    private void writeObject(java.io.ObjectOutputStream str)
    {
        new Object() {
            void readObject(java.io.ObjectInputStream x) {}
        };
    }
}
