package com.siyeh.igtest.style.chained_equality;

public class ChainedEquality
{
    public void fooBar()
    {
        boolean foo = fooBaz();
        boolean bar = fooBaz();
        boolean barangus = fooBaz();
        if(<warning descr="Chained equality comparison 'foo == bar == barangus'">foo == bar == barangus</warning>)
        {
            System.out.println("");
        }
        if (<warning descr="Chained equality comparison 'foo != bar == barangus'">foo != bar == barangus</warning>)
        {
            System.out.println("");
        }
    }

    private boolean fooBaz()
    {
        return true;
    }

    boolean boo(boolean a, boolean b, boolean c, boolean d, boolean e) {
        return <warning descr="Chained equality comparison 'a != b != c == d != e'">a != b != c  == d != e</warning>;
    }
}
