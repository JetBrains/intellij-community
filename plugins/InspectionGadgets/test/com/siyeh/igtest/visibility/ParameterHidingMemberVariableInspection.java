package com.siyeh.igtest.visibility;

import java.util.Set;
import java.util.HashSet;

public class ParameterHidingMemberVariableInspection
{
    private int bar = -1;

    public ParameterHidingMemberVariableInspection(int bar)
    {
        this.bar = bar;
    }

    public void setBar(int bar)
    {
        this.bar = bar;
    }

    public void foo(Object bar)
    {
        System.out.println("bar" + bar);
    }
}
