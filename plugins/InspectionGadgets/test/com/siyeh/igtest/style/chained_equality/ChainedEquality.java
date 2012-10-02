package com.siyeh.igtest.style.chained_equality;

public class ChainedEquality
{
    public void fooBar()
    {
        boolean foo = fooBaz();
        boolean bar = fooBaz();
        boolean barangus = fooBaz();
        if(foo == bar == barangus)
        {
            System.out.println("");
        }
        if (foo != bar == barangus)
        {
            System.out.println("");
        }
    }

    private boolean fooBaz()
    {
        return true;
    }

    boolean boo(boolean a, boolean b, boolean c, boolean d, boolean e) {
        return a != b != c  == d != e;
    }
}
