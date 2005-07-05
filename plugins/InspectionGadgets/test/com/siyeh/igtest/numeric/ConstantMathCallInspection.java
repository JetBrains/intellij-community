package com.siyeh.igtest.numeric;

public class ConstantMathCallInspection{
    public void foo()
    {
        Math.sin(0.0);
        Math.asin(1.0);
    }
}
