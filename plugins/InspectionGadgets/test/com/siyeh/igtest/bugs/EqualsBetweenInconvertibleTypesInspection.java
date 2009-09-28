package com.siyeh.igtest.bugs;

public class EqualsBetweenInconvertibleTypesInspection {

    public void foo()
    {
        final Integer foo = new Integer(3);
        final Double bar = new Double(3);
        foo.equals(bar);
    }
}
