package com.siyeh.igtest.confusing;

public class NestedAssignmentInspection
{
    public NestedAssignmentInspection()
    {
    }

    public void foo()
    {
        final int[] baz = new int[3];
        final int i;
        final int val = baz[i=2];
        System.out.println("i = " + i);
        System.out.println("val = " + val);
        for(int j=0,k=0;j<1000;j += 1,k += 1)
        {

        }
    }
}
