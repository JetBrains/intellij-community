package com.siyeh.igtest.abstraction;

public enum PublicMethodNotExposedInInterfaceEnum implements Interface {
    a, b, c;

    public void baz() {
    }

}
