package com.siyeh.igtest.bugs;

public class ComparisonOfShortAndChar
{

    public int foo()
    {
        char bar = 'c';
        short baz = (short) bar2();
        final boolean isEqual = bar == baz;
        System.out.println("isEqual = " + isEqual);
        final boolean isNotEqual = bar != baz;
        System.out.println("isEqual = " + isEqual);
        return 3;
    }

    private char bar2()
    {
        return (char) 3;
    }
}
