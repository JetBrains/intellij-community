package com.siyeh.igtest.confusing;

public class NegatedIfInspection
{

    public static void main(String[] args)
    {
        final boolean foo = baz();
        final boolean bar = baz();
        final Object bazoom = new Object();
        if(foo != bar)
        {
           System.out.println("");
        }
        else
        {
           System.out.println("");
        }
        if(bazoom != null)
        {
           System.out.println("");
        }
        else
        {
          System.out.println("");
        }
    }

    private static boolean baz()
    {
        return true;
    }
}
