package com.siyeh.igtest.internationalization;

public class StringConcatenationInspection
{
    public StringConcatenationInspection()
    {
    }

    public void foo()
    {
        final String concat = "foo" + "bar";
        System.out.println("concat = " + concat);
    }
}