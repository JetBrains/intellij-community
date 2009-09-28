package com.siyeh.igtest.verbose;

public class ReplaceAssignmentWithOperatorAssignmentInspection
{

    public ReplaceAssignmentWithOperatorAssignmentInspection()
    {

    }

    public void foo()
    {
        int x = 0;
        x = x + 3;
        x = x * 3;
        x = x / 3;
        x = x - 3;

        System.out.println("x = " + x);

        boolean b = true;
        b = b != false;

    }
}