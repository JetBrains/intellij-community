package com.siyeh.igtest.bugs;

public class ComparisonToNaNInspection {
    public void foo(double x)
    {
        if(x == Float.NaN)
        {
            return;
        }
    }
}
