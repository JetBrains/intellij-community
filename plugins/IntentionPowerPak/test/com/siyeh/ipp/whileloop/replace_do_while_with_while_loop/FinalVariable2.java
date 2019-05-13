package com.siyeh.ipp.whileloop.replace_do_while_with_while_loop;

class FinalVariable2 {

  void m(boolean b) {
    if (b) do<caret> {
      final int i = 10;
      System.out.println(i);
    } while (c());
  }

  boolean c() {
    return true;
  }
}