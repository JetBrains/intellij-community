package com.siyeh.igtest.performance;

public class ManualArrayCopyInspection
{

    public void fooBar()
    {
        final int[] q = new int[3];
        final int[] a = new int[3];
        for(int i = 0; i < a.length; i++)
            q[i] = a[i];
        for(int i = 0; i < a.length; i++)
            q[i+3] = a[i+4];
        for(int i = 0; i < a.length; i++)
        {
            q[i] = a[i];
        }
        for(int i = 0; i < a.length; i++)
        {
            q[i+3] = a[i];
        }
    }
}
