package com.siyeh.igtest.verbose;

public class UnnecessaryLocalVariableInspection {

    public int foo() {
        int a = 2;
        int b = a;
        return b;
    }

    public int bar() {
        int b = 3;
        return b;
    }

    public int baz() {
        int a;
        int b = 3;
        a = b;
        return a;
    }
}
