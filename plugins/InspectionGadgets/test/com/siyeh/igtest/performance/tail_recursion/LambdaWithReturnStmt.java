package com.siyeh.igtest.performance.tail_recursion;


interface I {
  int get();
}
class Test {

  private int bar() {
    final I s = new I() {
      @Override
      public int get() {
        return bar();
      }
    };
    return 0;
  }

}