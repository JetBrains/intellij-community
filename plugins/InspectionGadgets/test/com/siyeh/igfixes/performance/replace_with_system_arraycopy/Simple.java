package com.siyeh.igfixes.performance.replace_with_system_arraycopy;

class Simple {
  void foo(String[] source, Object[] target) {
    <caret>for (int k = 0; k < 5; k++) { // can be converted to System.arraycopy()
      target[k] = source[k];
    }
  }
}