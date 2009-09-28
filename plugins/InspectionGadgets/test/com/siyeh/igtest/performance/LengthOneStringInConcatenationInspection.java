
package com.siyeh.igtest.performance;

import java.io.IOException;

public class LengthOneStringInConcatenationInspection
{
    public LengthOneStringInConcatenationInspection()
    {
    }

    public void foo() throws IOException
    {
        final String s = "foo" + "i" + "bar" + " " + "baz" + "\t";
        System.out.println(s);
        final StringBuffer buffer = new StringBuffer();
        buffer.append("foo");
        buffer.append("f");
        final StringBuilder buffer2 = new StringBuilder();
        buffer2.append("foo");
        buffer2.append("f");
    }
}