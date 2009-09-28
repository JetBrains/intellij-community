package com.siyeh.igtest.confusing;

public class ChainedEqualityInspection
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
