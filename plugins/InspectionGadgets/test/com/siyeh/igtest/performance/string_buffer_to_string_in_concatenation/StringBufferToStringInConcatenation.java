package com.siyeh.igtest.performance.string_buffer_to_string_in_concatenation;

import java.io.IOException;
import java.util.*;

public class StringBufferToStringInConcatenation
{

    public void foo() {
        final StringBuffer buffer = new StringBuffer(3);
        String out = "foo" + buffer.<warning descr="Call to 'StringBuffer.toString()' in concatenation">toString</warning>();
        String in = 3 + buffer.toString();
        System.out.println("out = " + out);
    }

    public void bar() {
        final StringBuilder builder = new StringBuilder();
        String one = "bar" + builder.<warning descr="Call to 'StringBuilder.toString()' in concatenation">toString</warning>();
        String two = 6 + builder.toString();
        String three = builder.<warning descr="Call to 'StringBuilder.toString()' in concatenation">toString</warning>() + "  ";
        String s = 1 + 2 + "as df" + builder.<warning descr="Call to 'StringBuilder.toString()' in concatenation">toString</warning>() +  1 + "asdf";
    }
}