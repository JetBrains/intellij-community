package com.siyeh.igtest.style.unnecessary_interface_modifier;

public abstract interface UnnecessaryInterfaceModifierInspection {
    public static final int ONE = 1;
    int TWO = 2;

    public abstract void foo();

    void foo2();

    public static interface Inner {

    }
}
class Next {
    static interface Nested {}
}