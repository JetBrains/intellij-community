package com.siyeh.igtest.internationalization;

public class StringEqualsInspection
{
    public StringEqualsInspection()
    {
    }

    public void foo()
    {
        "foo".equals("bar");
    }
}