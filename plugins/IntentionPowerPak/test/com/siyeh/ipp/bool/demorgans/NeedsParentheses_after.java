package com.siyeh.ipp.bool.demorgans;

class NeedsParentheses {

  void foo(boolean a, boolean b) {
      //between operand
      if (!((!a || !b) && (a || //inside nested
              b))){}
  }
}