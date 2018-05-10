package com.siyeh.igfixes.performance.replace_with_system_arraycopy;

class Simple {
  void foo(String[] source, Object[] target) {
    int[] src = new int[]{1, 2, 3};
    int offset = 10;
    int[] dst = new int[12];
    <caret>for (int i = 0; i < src.length - offset; i++) {
      src[i] = src[i + offset];
    }
  }
}