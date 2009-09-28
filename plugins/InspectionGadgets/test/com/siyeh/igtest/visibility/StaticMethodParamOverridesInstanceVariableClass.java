package com.siyeh.igtest.visibility;

public class StaticMethodParamOverridesInstanceVariableClass extends StaticMethodToOverrideClass
{
    private int bar;

    public static void fooBar(int bar)
    {
        System.out.println("bar = " + bar);
    }
}
