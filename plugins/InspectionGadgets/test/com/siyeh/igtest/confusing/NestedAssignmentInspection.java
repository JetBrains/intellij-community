package com.siyeh.igtest.confusing;

public class NestedAssignmentInspection
{
    public NestedAssignmentInspection() throws Exception
    {
        super();
    }

    public void foo()
    {
        final int[] baz = new int[3];
         int i;
         int val = baz[i=2];
        System.out.println("i = " + i);
        System.out.println("val = " + val);
        for(int j=0,k=0;j<1000;j += 1,k += 1)
        {

        }
        barangus(i=2, val=3);
        System.out.println("i = " + i);
        System.out.println("val = " + val);
    }

    private void barangus(int i, int val){
    }
}
