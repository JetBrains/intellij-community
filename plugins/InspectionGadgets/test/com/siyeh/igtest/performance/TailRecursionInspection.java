package com.siyeh.igtest.performance;

import java.io.IOException;

public class TailRecursionInspection
{
    public TailRecursionInspection()
    {
    }

    public int foo() throws IOException
    {
        return foo();
    }

    public int factorial(int val)
    {
        return factorial(val, 1);
    }

    public int factorial(int val, int runningVal)
    {
        if(val == 1)
        {
            return runningVal;
        }
        else
        {
            return factorial(val-1, runningVal * val);
        }
    }
}