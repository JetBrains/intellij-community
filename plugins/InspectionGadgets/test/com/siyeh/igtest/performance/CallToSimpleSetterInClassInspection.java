package com.siyeh.igtest.performance;

public class CallToSimpleSetterInClassInspection {
    private int intVal;

    public void setIntVal(int x) {
        intVal = x;
    }

    public void doSetIntVal(int x)
    {
        setIntVal(x);
    }
}
