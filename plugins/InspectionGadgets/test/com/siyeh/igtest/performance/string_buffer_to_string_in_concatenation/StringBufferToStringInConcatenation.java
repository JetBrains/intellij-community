package com.siyeh.igtest.performance.string_buffer_to_string_in_concatenation;

import java.io.IOException;
import java.util.*;

public class StringBufferToStringInConcatenation
{

    public void foo() {
        final StringBuffer buffer = new StringBuffer(3);
        String out = "foo" + buffer.toString();
        String in = 3 + buffer.toString();
        System.out.println("out = " + out);
    }

    public void bar() {
        final StringBuilder builder = new StringBuilder();
        String one = "bar" + builder.toString();
        String two = 6 + builder.toString();
        String three = builder.toString() + "  ";
        String s = 1 + 2 + "as df" + builder.toString() +  1 + "asdf";
    }
}