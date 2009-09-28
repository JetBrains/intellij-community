package com.siyeh.igtest.maturity;

import java.io.IOException;
import java.io.PrintStream;

public class SystemOutErrInspection
{
    public SystemOutErrInspection()
    {
    }

    public void foo() throws IOException
    {                 
        System.out.println(0);
        System.err.println(0);
        final PrintStream out = System.out;
        final PrintStream err = System.err;

    }
}