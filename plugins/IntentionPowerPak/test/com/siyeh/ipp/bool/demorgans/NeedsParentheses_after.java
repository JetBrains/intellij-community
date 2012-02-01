package com.siyeh.ipp.bool.demorgans;

class NeedsParentheses {

  void foo(boolean a, boolean b) {
    if (!((!a || !b) && (a || b))){}
  }
}