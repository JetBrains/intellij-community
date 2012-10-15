package com.siyeh.igtest.bugs.string_concatenation_in_format_call;



public class StringContenationInFormatCall {

  void foo(int i) {
    String.format("a" + "b" + i);
    String.format("c: " + i);
  }
}