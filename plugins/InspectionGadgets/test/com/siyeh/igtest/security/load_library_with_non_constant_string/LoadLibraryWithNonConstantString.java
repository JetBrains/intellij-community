package com.siyeh.igtest.security;

import java.io.IOException;

public class LoadLibraryWithNonConstantString extends ClassLoader
{
    public LoadLibraryWithNonConstantString()
    {
    }

    public void foo() throws IOException
    {
        String i = bar();
        System.<warning descr="Call to 'System.loadLibrary()' with non-constant argument">loadLibrary</warning>("foo" + i);
        System.loadLibrary("foo");
    }

    private String bar() {
        return "bar";
    }
}