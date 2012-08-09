package com.siyeh.igtest.performance.redundant_string_format_call;

import static java.lang.String.format;



public class RedundantStringFormatCall {

    public static final String A = String.format("%n");
  String b = String.format("no parameters");
  String c = String.format("asdf%n" +
                           "asdf%n");
          String d = String.format("asdf" + "asdf" + "asdf%n");
    String e = format("test");
}