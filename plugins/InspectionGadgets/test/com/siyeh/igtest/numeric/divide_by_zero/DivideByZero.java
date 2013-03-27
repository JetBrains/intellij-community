package com.siyeh.igtest.numeric.divide_by_zero;

public class DivideByZero {

  int divide(int num) {
    return num / 3 / 0;
  }

  int rest(int num) {
    return num % 0 % 1;
  }

}