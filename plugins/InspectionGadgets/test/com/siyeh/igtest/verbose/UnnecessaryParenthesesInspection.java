package com.siyeh.igtest.verbose;

import java.util.List;
import java.util.ArrayList;

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
        final int hash = ((this).hashCode());
        final int hash2 = (("x" + "y").hashCode());
        List list = new ArrayList();
        (list.subList(1, 2)).get(0);
    }

    public boolean testParenRedundancy(boolean a, boolean b, boolean c) {
        return a || (b || c);
    }

    public int arg(int i,int j, int k) {
        return i + (j + k);
    }
}