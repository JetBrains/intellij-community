package com.siyeh.igtest.performance;

import java.io.IOException;

public class StringToStringInspection
{
    public StringToStringInspection()
    {
    }

    public void foo() throws IOException
    {
        "true".toString();
    }
}