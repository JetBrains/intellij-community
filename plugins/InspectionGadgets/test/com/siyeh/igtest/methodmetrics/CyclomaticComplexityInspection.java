package com.siyeh.igtest.methodmetrics;

public class CyclomaticComplexityInspection
{
    public void fooBar()
    {
        int i = 0;
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        System.out.println("i = " + i);
    }

    private boolean bar()
    {
        return true;
    }
}
