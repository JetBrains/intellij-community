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
}