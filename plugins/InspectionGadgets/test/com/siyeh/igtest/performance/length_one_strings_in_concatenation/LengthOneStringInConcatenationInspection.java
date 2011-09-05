package com.siyeh.igtest.performance.length_one_strings_in_concatenation;

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
        System.out.println("asdf" + 1 + "a");
        System.out.println("asdf" + "b" + 2);
        System.out.println(1 + "b" + "asdf");
        System.out.println("a" + );
    }
}