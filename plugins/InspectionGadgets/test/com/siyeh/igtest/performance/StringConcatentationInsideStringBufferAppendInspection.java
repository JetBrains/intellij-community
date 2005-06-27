package com.siyeh.igtest.performance;

import java.io.IOException;
import java.util.*;

public class StringConcatentationInsideStringBufferAppendInspection
{
    public StringConcatentationInsideStringBufferAppendInspection()
    {
    }

    public void foo() throws IOException
    {
        final StringBuffer buffer = new StringBuffer(3);
        buffer.append("foo" + 3 + "bar");
        buffer.append("foo" + "bar");
    }
}