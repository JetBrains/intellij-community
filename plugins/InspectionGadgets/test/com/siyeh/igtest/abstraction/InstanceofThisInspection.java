package com.siyeh.igtest.abstraction;

public abstract class InstanceofThisInspection
{
    public void foo(String[] args)
    {
        if(this instanceof InstanceofThisInspectionChild)
        {
            return;
        }
    }

}
