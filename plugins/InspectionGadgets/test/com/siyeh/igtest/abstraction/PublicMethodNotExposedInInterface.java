package com.siyeh.igtest.abstraction;

public class PublicMethodNotExposedInInterface implements Interface {
    public void foo() {

    }

    public void baz() {
        bar2();
    }

    public static void bar() {

    }

    private void bar2() {

    }

}
