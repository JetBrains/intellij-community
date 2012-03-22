package com.siyeh.igtest.bugs.math_random_cast_to_int;

public class MathRandomCastToInt {

  void foo() {
    int runs = (int) Math.random() * 1000000 * 2;
  }
}
