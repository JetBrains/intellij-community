package com.siyeh.igtest.performance;

public class StringReplaceableByStringBufferInspection {
    public void foo()
    {
        String buffer = "bar";
        buffer += "foo";
        System.out.println(buffer);
    }

    public void foobar()
    {
        String buffer = "bar";
        buffer = buffer + "foo";
        System.out.println(buffer);
    }
    
    public void foobaz()
    {
        final String buffer = "bar";
        System.out.println(buffer);
    }
}
