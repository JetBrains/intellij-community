package com.siyeh.igtest.security;

import java.io.IOException;

public class RuntimeExecWithNonConstantStringInspection
{
    public RuntimeExecWithNonConstantStringInspection()
    {
    }

    public void foo() throws IOException
    {
        String i = bar();
        final Runtime runtime = Runtime.getRuntime();
        runtime.exec("foo" + i);
        runtime.exec("foo");
    }

    private String bar() {
        return "bar";
    }
}