package com.siyeh.igtest.performance;

import java.io.IOException;
import java.util.*;

public class StringBufferToStringInConcatenatinInspection
{
    public StringBufferToStringInConcatenatinInspection()
    {
    }

    public void foo() throws IOException
    {
        final StringBuffer buffer = new StringBuffer(3);
        String out = "foo" + buffer.toString();
        System.out.println("out = " + out);
        Object i = null;
        i.toString();
    }
}