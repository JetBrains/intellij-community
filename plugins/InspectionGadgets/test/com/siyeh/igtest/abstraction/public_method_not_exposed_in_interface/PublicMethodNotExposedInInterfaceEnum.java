package com.siyeh.igtest.abstraction;

public enum PublicMethodNotExposedInInterfaceEnum implements Interface {
    a, b, c;

    public void baz() {
    }

    public void <warning descr="'public' method 'foo()' is not exposed via an interface">foo</warning>() {}

}
interface Interface {
    void baz();
}

