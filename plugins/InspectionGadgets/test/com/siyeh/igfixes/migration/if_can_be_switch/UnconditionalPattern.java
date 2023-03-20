package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class Test {
  void test(Object object) {
    <caret>if (object instanceof String) {
      System.out.println("string");
    } else if (object instanceof Object) {
      System.out.println("object");
    }
  }
}