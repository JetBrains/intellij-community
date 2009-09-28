package com.siyeh.igtest.exceptionHandling;

public class ReturnFromFinallyBlockInspection
{
    public void foo()
    {
        try
        {

        }
        finally
        {
            return;
        }
    }

    public int bar()
    {
        try
        {
           return 4;
        }
        finally
        {
            return 3;
        }
    }
}
