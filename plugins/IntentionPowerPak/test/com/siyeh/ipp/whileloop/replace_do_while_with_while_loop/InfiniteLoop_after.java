package com.siyeh.ipp.whileloop.replace_do_while_with_while_loop;

class InfiniteLoop {
  void m() {
      /*before code block*/
      //after end
      while ((true)) { //comment
          int i = 10;
          System.out.println(i);
      }
  }
}