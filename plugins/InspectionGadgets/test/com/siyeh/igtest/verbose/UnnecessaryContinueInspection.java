package com.siyeh.igtest.verbose;

public class UnnecessaryContinueInspection
{

    public UnnecessaryContinueInspection()
    {
        for(;;)
        {
            continue;
        }
    }
    public void foo()
    {
        while(true)
            continue;
    }


}