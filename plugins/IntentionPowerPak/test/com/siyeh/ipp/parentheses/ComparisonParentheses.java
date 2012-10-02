package com.siyeh.ipp.parentheses;

class ComparisonParenthese {

  void foo(Object a, boolean b) {
    final boolean c = b == (a <caret>!= null);
    boolean d = c == (1 < 3<caret>);
  }
}