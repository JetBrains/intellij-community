package com.siyeh.igtest.internationalization;

public class StringEqualsIgnoreCaseInspection
{
    public StringEqualsIgnoreCaseInspection()
    {
    }

    public void foo()
    {
        "foo".equalsIgnoreCase("bar");
    }
}