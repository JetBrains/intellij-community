package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class Test {
  void test(Object object) {
      switch (object) {
          case String s -> System.out.println("string");
          case Object o -> System.out.println("object");
      }
  }
}