package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {
  public static void test(Object obj) {
      switch (obj) {
          case String s1 -> {
              String s = s1;
              s = s.repeat(2);
              System.out.println(s);
              System.out.println(s1);
          }
          case Integer i -> System.out.println(i * 2);
          case null, default -> System.out.println();
      }
  }
}