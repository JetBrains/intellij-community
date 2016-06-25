package com.siyeh.igtest.bugs.math_random_cast_to_int;

public class MathRandomCastToInt {

  void foo() {
    int runs = (int) <warning descr="'Math.random()' cast to 'int' is always rounded down to '0'">Math.random()</warning> * 1000000 * 2;
    long random = (long) <warning descr="'Math.random()' cast to 'int' is always rounded down to '0'">Math.random()</warning> * 10;
  }
}
