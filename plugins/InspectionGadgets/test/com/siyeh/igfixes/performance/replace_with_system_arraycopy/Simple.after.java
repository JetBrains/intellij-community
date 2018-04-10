package com.siyeh.igfixes.performance.replace_with_system_arraycopy;

class Simple {
  void foo(String[] source, Object[] target) {
      /*declr*/
      //condition
      //before update
      //after update
      // can be converted to System.arraycopy()
      //body
      System.arraycopy(source, 0, target, 0, 5);
  }
}