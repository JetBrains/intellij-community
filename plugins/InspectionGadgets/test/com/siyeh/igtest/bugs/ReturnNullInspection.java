package com.siyeh.igtest.bugs;

public class ReturnNullInspection
{

    public Object bar()
    {
        return null;
    }

    public int[] bar2()
    {
        return null;
    }
}