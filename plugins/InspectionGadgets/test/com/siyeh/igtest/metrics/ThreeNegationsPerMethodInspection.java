package com.siyeh.igtest.metrics;

public class ThreeNegationsPerMethodInspection
{
    public void okayMethod()
    {
        if(!!!true)
        {
            return;
        }
    }

    public void badMethod()
    {
        if(!!!!true)
        {
            return;
        }
    }

    public void badMethod2()
    {
        if(!!!true && 3 !=4)
        {
            return;
        }
    }
}
