package com.siyeh.igtest.threading;

public class DoubleCheckedLockingInspection
{
    private static Object s_instance;

    public static Object foo()
    {
        if(s_instance == null)
        {
            synchronized(DoubleCheckedLockingInspection.class)
            {
                if(s_instance == null)
                {
                    s_instance = new Object();
                }
            }
        }
        return s_instance;
    }
}
