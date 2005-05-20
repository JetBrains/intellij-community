package com.siyeh.igtest.performance;

import java.util.ArrayList;
import java.util.List;

public class CallToSimpleGetterInClassInspection {
    private int intVal;
    private ArrayList listVal;

    public int getIntVal() {
        return intVal;
    }

    public int returnIntVal()
    {
        return getIntVal();
    }

    public List getListVal() {
        return listVal;
    }

    public List returnListVal()
    {
        return getListVal();
    }

}
