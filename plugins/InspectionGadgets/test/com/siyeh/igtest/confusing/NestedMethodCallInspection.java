package com.siyeh.igtest.confusing;

import java.util.ArrayList;

public class NestedMethodCallInspection extends ArrayList{
    private int baz = bar(foo());

    public NestedMethodCallInspection(int initialCapacity) {
        super(Math.abs(initialCapacity));
    }

    public NestedMethodCallInspection() {
        this(Math.abs(3));
    }

    public int foo()
    {
        return 3;
    }
    public int bar(int val)
    {
        return 3+val;
    }

    public int baz()
    {
        bar(Math.abs(3));
        return bar(foo());
    }

    public int barangus()
    {
        return bar(foo()+3);
    }
}
