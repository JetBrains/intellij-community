package com.siyeh.igtest.bugs.class_new_instance;

public class ClassNewInstance {

    void good() throws IllegalAccessException, InstantiationException {
        String.class.newInstance();
    }

    Object newInstance() {
        return null;
    }

    void bad() {
        newInstance();
    }

    void alsoBad(XX xx) {
        xx.newInstance();
    }

}