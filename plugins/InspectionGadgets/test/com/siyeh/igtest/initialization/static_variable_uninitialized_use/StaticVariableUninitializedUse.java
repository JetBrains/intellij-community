package com.siyeh.igtest.initialization.static_variable_uninitialized_use;

public class StaticVariableUninitializedUse {

    static Integer i;
    static String s;

    static {
        System.out.println(StaticVariableUninitializedUse.s);
    }
    public static void main(String[] args) {
        if (s instanceof Object) {}
        if (i == 42) {
            System.out.println("Unbelievable");
        }
        System.out.println("only warn once in a method" + i);
    }

    static int foo() {
        return i;
    }
}
