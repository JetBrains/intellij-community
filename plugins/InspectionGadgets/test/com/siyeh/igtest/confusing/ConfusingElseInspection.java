package com.siyeh.igtest.confusing;

public class ConfusingElseInspection
{
    public static void main(String[] args)
    {
        if(foo())
        {
            return;
        }
        else
        {
            System.out.println("ConfusingElseInspection.main");
        }
        bar();
    }

    private static void bar()
    {
    }

    private static boolean foo()
    {
        return true;
    }
}
