package com.siyeh.igtest.maturity;

import java.io.IOException;
import java.io.PrintStream;

public class ThreadDumpStackInspection
{
    public ThreadDumpStackInspection()
    {
    }

    public void foo()
    {
        Thread.dumpStack();
    }
}