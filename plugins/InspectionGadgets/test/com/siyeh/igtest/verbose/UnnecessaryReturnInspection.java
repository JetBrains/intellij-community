package com.siyeh.igtest.verbose;

public class UnnecessaryReturnInspection
{

    public UnnecessaryReturnInspection()
    {
        return;
    }

    public void foo()
    {
        return;
    }
    public void foo2()
    {
        {
            {
                return;
            }
        }
    }

    public void bar()
    {
        if(true)
        {
            return;
        }
    }

    public void barzoom()
    {
        while(true)
        {
            return;
        }
    }

}