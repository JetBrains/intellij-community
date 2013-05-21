package com.siyeh.ipp.whileloop.replace_do_while_with_while_loop;

class WithoutBraces {
  void test(boolean condition) {
      if (condition) {
          System.out.println();
          while (condition) System.out.println();
      }
  }
}