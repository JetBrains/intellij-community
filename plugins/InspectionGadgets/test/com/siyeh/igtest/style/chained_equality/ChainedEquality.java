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
    }

    private boolean fooBaz()
    {
        return true;
    }
}
