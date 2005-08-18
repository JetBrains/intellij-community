package com.siyeh.igtest.performance;

import java.io.IOException;
import java.util.*;

public class StringConcatetationInsideStringBufferAppendInspection
{
    public StringConcatetationInsideStringBufferAppendInspection()
    {
    }

    public void foo(String other) throws IOException
    {
        final StringBuffer buffer = new StringBuffer(3);
        buffer.append(other + "foo" + 3 + "bar");
        buffer.append("this" + "foo" + "bar" + other);
        buffer.append("pure" + "constant");
    }

    static String foo(int i) {
        new StringBuffer().append("This is number " + (i+1));
        return new StringBuilder().append(i + i + " a '" + "b").toString();
    }
}