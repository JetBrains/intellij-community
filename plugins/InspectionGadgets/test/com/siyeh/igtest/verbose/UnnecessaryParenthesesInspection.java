package com.siyeh.igtest.verbose;

public class UnnecessaryParenthesesInspection
{

    public int foo()
    {
        final String s = "foo" + (3 + 4);
        final String t = ("foo" + 3) + 4;
        return (3);
    }

    public void bar()
    {
        final int x = (3 + 4)*5;
        final int q = (3 + 4)-5;
        final int y = 3 + (4*5);
        final int z = 3 + (4*(3*5));
        final int k = 4 * (3 * 5);
        System.out.println("q = " + q);
        System.out.println("x = " + x);
        System.out.println("y = " + y);
        System.out.println("z = " + z);
        System.out.println("k = " + k);
    }

}