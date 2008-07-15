package com.siyeh.igtest.style.unnecessary_parentheses;

import java.util.List;
import java.util.ArrayList;

public class UnnecessaryParenthesesInspection
{

    public int foo()
    {
        final String s = "foo" + (3 + 4); // do not warn here
        final String t = ("foo" + 3) + 4; // but warn here
        return (3); // warn
    }

    public void bar()
    {
        final int x = (3 + 4)*5; // no warn
        final int q = (3 + 4)-5; // warn
        final int p = 3 + (4-5); // no warn
        final int y = 3 + (4*5); // warn
        final int z = 3 + (4*(3*5)); // 2 warnings
        final int k = 4 * (3 * 5); // warn
        final int hash = ((this).hashCode()); // 2 warnings
        final int hash2 = (("x" + "y").hashCode()); // 1 warning
        List list = new ArrayList();
        (list.subList(1, 2)).get(0); // warn
    }

    public boolean testParenRedundancy(boolean a, boolean b, boolean c) {
        return a || (b || c); // warn
    }

    public int arg(int i,int j, int k) {
        int result = 4 - (3 - 1); // no warn!
        return i + (j + k); // warn
    }

    public boolean is(int value) {
        int i = (new Integer(33)).intValue(); // warn
        return value < 0 || value > 10
                || (value != 5); // warn 
    }
}