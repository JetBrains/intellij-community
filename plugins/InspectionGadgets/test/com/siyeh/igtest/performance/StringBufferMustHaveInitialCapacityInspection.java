package com.siyeh.igtest.performance;

import java.io.IOException;

public class StringBufferMustHaveInitialCapacityInspection
{
    public StringBufferMustHaveInitialCapacityInspection()
    {
    }

    public void foo() throws IOException
    {
        new StringBuffer();
        new StringBuffer(3);
        new StringBuffer("foo");
        new StringBuilder();
        new StringBuilder(3);
        new StringBuilder("foo");

    }
}