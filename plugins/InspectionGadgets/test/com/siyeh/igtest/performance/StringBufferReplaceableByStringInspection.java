package com.siyeh.igtest.performance;

public class StringBufferReplaceableByStringInspection {
    public void foo()
    {
        final StringBuffer buffer = new StringBuffer();
        System.out.println(buffer.toString());
    }

    public void foo2()
    {
        final StringBuffer buffer = new StringBuffer("foo").append("bar");
        System.out.println(buffer.toString());
    }
}
