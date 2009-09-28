package com.siyeh.igtest.performance;

import java.util.List;
import java.util.ArrayList;

public class CallToSimpleSetterInClassInspection {
    private int intVal;
    private List listVal;

    public void setIntVal(int x) {
        intVal = x;
    }

    public void doSetIntVal(int x)
    {
        final CallToSimpleSetterInClassInspection foo = new CallToSimpleSetterInClassInspection();
        setIntVal(x);
        foo.setIntVal(x);
    }

    public void setListVal(ArrayList listVal) {
        this.listVal = listVal;
    }

    public void doSetListValue(ArrayList x)
    {
        setListVal(x);
    }
}
