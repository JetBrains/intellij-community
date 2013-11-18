package com.siyeh.ipp.whileloop.replace_do_while_with_while_loop;

class InfiniteLoop {
  void m() {
      while ((true)) {
          int i = 10;
          System.out.println(i);
      }
  }
}