package com.siyeh.ipp.whileloop.replace_do_while_with_while_loop;

class InfiniteLoop {
  void m() {
      /*before code block*/
      while ((true)) { //comment
          int i = 10;
          System.out.println(i);
      } //after end
  }
}