package com.siyeh.ipp.whileloop.replace_while_with_do_while_loop;

class Regular {

  void m() {
      if (b()) {
          do {
              System.out.println(1);
          } while (b());
      }
  }

  boolean b() {
    return true;
  }
}