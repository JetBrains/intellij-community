package com.siyeh.igtest.security;

import java.io.IOException;

public class LoadLibraryWithNonConstantStringInspection extends ClassLoader
{
    public LoadLibraryWithNonConstantStringInspection()
    {
    }

    public void foo() throws IOException
    {
        String i = bar();
        System.loadLibrary("foo" + i);
        System.loadLibrary("foo");
    }

    private String bar() {
        return "bar";
    }
}