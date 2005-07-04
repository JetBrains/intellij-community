package com.siyeh.igtest.performance;

import java.io.IOException;

public class SubstringZeroInspection
{
    public SubstringZeroInspection()
    {
    }

    public void foo() throws IOException
    {
        String foo = "true".substring(0);
    }
}