package com.siyeh.igtest.bugs;

public class IntegerDivisionInFloatingPointContextInspection{
    public void foo()
    {
        float x = 3.0F + 3/5;
    }
}
