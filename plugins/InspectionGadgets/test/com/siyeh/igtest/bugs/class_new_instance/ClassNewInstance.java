package com.siyeh.igtest.bugs.class_new_instance;

public class ClassNewInstance {

    void good() throws IllegalAccessException, InstantiationException {
        String.class.<warning descr="Call to 'newInstance()' may throw undeclared checked exceptions">newInstance</warning>();
    }

    Object newInstance() {
        return null;
    }

    void bad() {
        newInstance();
    }

    void alsoBad(Class<XX> xx) throws IllegalAccessException {
        xx.<warning descr="Call to 'newInstance()' may throw undeclared checked exceptions">newInstance</warning>();
    }

}
class XX {}