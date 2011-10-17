package com.siyeh.igtest.assignment.replace_assignment_with_operator_assignment;

public class ReplaceAssignmentWithOperatorAssignment
{

    public ReplaceAssignmentWithOperatorAssignment()
    {

    }

    public void foo()
    {
        int x = 0;
        x = x + 3;
        x = x * 3;
        x = x / 3;
        x = x - 3;
        x = x + 2 + 2 + 2;

        System.out.println("x = " + x);

        boolean b = true;
        b = b != false;

      x = x / 2 / 4;
      x = x >> 1 >> 1;
    }
}