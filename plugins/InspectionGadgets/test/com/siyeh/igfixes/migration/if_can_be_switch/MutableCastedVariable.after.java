package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {
  public static void test(Object obj) {
      switch (obj) {
          case String string -> {
              String s = string;
              s = s.repeat(2);
              System.out.println(s);
              System.out.println(string);
          }
          case Integer i -> System.out.println(i * 2);
          case null, default -> System.out.println();
      }
  }
}