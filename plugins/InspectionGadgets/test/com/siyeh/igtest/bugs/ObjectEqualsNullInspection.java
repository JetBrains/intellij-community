package com.siyeh.igtest.bugs;

public class ObjectEqualsNullInspection {
    private Object lock = new Object();

    public boolean foo()
    {
        return lock.equals(null); 
    }
}
