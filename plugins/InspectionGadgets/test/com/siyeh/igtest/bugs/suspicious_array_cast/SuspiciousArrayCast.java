package com.siyeh.igtest.bugs.suspicious_array_cast;

class SuspiciousArrayCast {

  private Long[] longs = (Long[])getNumbers();
  private Number[] numbers = (Number[])getNumbers();

  private Number[] getNumbers() {
    return new Number[]{1L, 2L, 4L};
  }
}