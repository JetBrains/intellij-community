package com.siyeh.igtest.performance;

public class CallToSimpleGetterInClassInspection {
    private int intVal;

    public int getIntVal() {
        return intVal;
    }

    public int returnIntVal()
    {
        return getIntVal();
    }
}
