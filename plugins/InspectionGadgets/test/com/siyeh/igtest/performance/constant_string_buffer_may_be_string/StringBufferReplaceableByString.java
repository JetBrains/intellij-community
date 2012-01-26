package com.siyeh.igtest.performance.constant_string_buffer_may_be_string;

public class StringBufferReplaceableByString {

  StringBuilder foo() {
    StringBuilder builder = new StringBuilder("asdfasdfasdf");
    StringBuffer buffer = new StringBuffer("test");
    StringBuilder result = new StringBuilder("return");
    return result;
  }
}
