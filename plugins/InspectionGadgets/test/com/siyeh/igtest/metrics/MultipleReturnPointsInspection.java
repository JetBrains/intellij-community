package com.siyeh.igtest.metrics;

public class MultipleReturnPointsInspection
{
    public void fooBar()
    {
        if(barangus())
        {
            return;
        }
    }

    private boolean barangus()
    {
        if(true)
        {
            return true;
        }
        return false;
    }
}
