package com.siyeh.igtest.performance;

public class StringBufferReplaceableByStringBuilderInspection {
    public void foo()
    {
        final StringBuffer buffer = new StringBuffer();
        buffer.append("bar");
        buffer.append("bar");
        System.out.println(buffer.toString());
    }

    public StringBuffer foo2()
    {
        final StringBuffer buffer = new StringBuffer();
        buffer.append("bar");
        buffer.append("bar");
        System.out.println(buffer.toString());
        return buffer;
    }
}
