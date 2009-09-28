package com.siyeh.igtest.internationalization;

public class StringCompareToInspection
{
    public StringCompareToInspection()
    {
    }

    public void foo()
    {
        "foo".compareTo("bar");
    }
}