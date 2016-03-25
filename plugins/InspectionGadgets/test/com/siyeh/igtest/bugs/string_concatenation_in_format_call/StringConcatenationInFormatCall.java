package com.siyeh.igtest.bugs.string_concatenation_in_format_call;



public class StringConcatenationInFormatCall {

  void foo(int i) {
    String.<warning descr="'format()' call has a String concatenation argument">format</warning>("a" + "b" + i);
    String.<warning descr="'format()' call has a String concatenation argument">format</warning>("c: " + i);
  }
}