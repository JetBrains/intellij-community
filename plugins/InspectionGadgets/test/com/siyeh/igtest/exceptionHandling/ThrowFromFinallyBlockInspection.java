package com.siyeh.igtest.exceptionHandling;

public class ThrowFromFinallyBlockInspection
{
    public void foo() throws Exception
    {
        try
        {
            return;
        }
        finally
        {
            throw new Exception();
        }
    }

    public void bar() throws Exception
    {
        try
        {
            return;
        }
        finally
        {
            try
            {
                throw new Exception();
            }
            finally
            {
                throw new Exception();
            }
        }
    }

}
