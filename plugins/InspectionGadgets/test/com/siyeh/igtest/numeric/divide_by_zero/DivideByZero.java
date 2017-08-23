package com.siyeh.igtest.numeric.divide_by_zero;

public class DivideByZero {

  int divide(int num) {
    return num / 3 / 0;
  }

  int rest(int num) {
    return num % 0 % 1;
  }

  void assignment(int i, double d) {
    i /= 1-1;
    d %= 0;
    i /= d;
  }

  // IDEABKL-7552 Report inspection with the highest severity
  void test(int size) {
    if (size == 0) {
      int x = 42 / size;
    }
  }

  void test2(int size) {
    if (size > 0) {
      System.out.println(43 / size);
      return;
    }
    if (size < 0) {
      System.out.println(41 / size);
      return;
    }
    System.out.println(42 / size);
  }

}