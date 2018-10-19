package com.siyeh.igtest.assignment.replace_assignment_with_operator_assignment;

public class ReplaceAssignmentWithOperatorAssignment
{

    public ReplaceAssignmentWithOperatorAssignment()
    {

    }

    public void foo()
    {
        int x = 0;
        <warning descr="'x = x + 3' could be simplified to 'x += 3'">x = x + 3</warning>;
        <warning descr="'x = x * 3' could be simplified to 'x *= 3'">x = x * 3</warning>;
        <warning descr="'x = x / 3' could be simplified to 'x /= 3'">x = x / 3</warning>;
        <warning descr="'x = x - 3' could be simplified to 'x -= 3'">x = x - 3</warning>;
        <warning descr="'x = x + 2 + 2 + 2' could be simplified to 'x += 2 + 2 + 2'">x = x + 2 + 2 + 2</warning>;

        System.out.println("x = " + x);

        boolean b = true;
        b = b != false;

      x = x / 2 / 4;
      x = x >> 1 >> 1;
      <warning descr="'x = x * 2 * 2' could be simplified to 'x *= 2 * 2'">x = x * 2 * 2</warning>;
      float f = 1;
      f = f * 2 * 2;
      int a = Integer.MAX_VALUE;
      double d = Double.MAX_VALUE;
      (a) = (byte)(a + (d - d));// should not warn here
      <warning descr="'(a) = (int)(a + (d - d))' could be simplified to '(a) += (d - d)'">(a) = (int)(a + (d - d))</warning>;// should warn here
    }
}