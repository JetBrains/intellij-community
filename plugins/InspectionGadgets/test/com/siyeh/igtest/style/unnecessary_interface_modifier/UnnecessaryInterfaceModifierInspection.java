package com.siyeh.igtest.style.unnecessary_interface_modifier;

public abstract interface UnnecessaryInterfaceModifierInspection {
    public static final int ONE = 1;
    int TWO = 2;

    public abstract void foo();

    void foo2();

    public abstract static interface Inner {

    }
}
interface Next {
    static interface Nested {}

    public abstract static class Inner {}
    public abstract static interface Inner2 {}


    public final class Sub extends Inner {}
}