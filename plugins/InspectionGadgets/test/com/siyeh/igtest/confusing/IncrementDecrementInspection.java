package com.siyeh.igtest.confusing;

public class IncrementDecrementInspection
{
    public IncrementDecrementInspection()
    {
    }

    public void foo()
    {
        final int[] baz = new int[3];
        int i = 0;
        i++;
        final int val = baz[i++];
        final int val2 = baz[i--];
        final int val3 = baz[++i];
        final int val4 = baz[--i];
        System.out.println("i = " + i);
        System.out.println("val = " + val);
    }
}
