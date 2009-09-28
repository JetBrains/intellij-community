package com.siyeh.igtest.maturity;

import java.io.IOException;
import java.io.PrintStream;

public class ThrowablePrintStackTraceInspection
{
    public ThrowablePrintStackTraceInspection()
    {
    }

    public void foo()
    {
        new Throwable().printStackTrace();
    }
}