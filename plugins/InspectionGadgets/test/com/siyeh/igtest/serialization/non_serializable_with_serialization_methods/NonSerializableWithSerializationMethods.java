package com.siyeh.igtest.serialization.non_serializable_with_serialization_methods;



public class <warning descr="Non-serializable class 'NonSerializableWithSerializationMethods' defines 'readObject()' and 'writeObject()'">NonSerializableWithSerializationMethods</warning>
{
    private static final long serialVersionUID = 1;

    private void readObject(java.io.ObjectInputStream str)
    {

    }

    private void writeObject(java.io.ObjectOutputStream str)
    {
        new <warning descr="Non-serializable anonymous class extending 'Object' defines 'readObject()'">Object</warning>() {
            void readObject(java.io.ObjectInputStream x) {}
        };
    }
}

record <warning descr="Non-serializable record 'R' defines 'readObject()' and 'writeObject()'">R</warning>() {

    private void readObject(java.io.ObjectInputStream str) {
    }

    private void writeObject(java.io.ObjectOutputStream str) {
    }
}
interface <warning descr="Non-serializable interface 'I' defines 'readObject()' and 'writeObject()'">I</warning> {

  void readObject(java.io.ObjectInputStream str);

  void writeObject(java.io.ObjectOutputStream str);
}