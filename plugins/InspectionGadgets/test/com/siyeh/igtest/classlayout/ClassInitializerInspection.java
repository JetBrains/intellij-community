package com.siyeh.igtest.classlayout;

public class ClassInitializerInspection {
    static private int foo;

    static {
        foo = 3;
    }

    {
        foo = 3;
    }
}
