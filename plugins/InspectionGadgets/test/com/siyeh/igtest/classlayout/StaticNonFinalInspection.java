package com.siyeh.igtest.classlayout;

public class StaticNonFinalInspection
{
    private static int s_foo = 3;

    static 
    {
        System.out.println("s_foo = " + s_foo);
    }
}
