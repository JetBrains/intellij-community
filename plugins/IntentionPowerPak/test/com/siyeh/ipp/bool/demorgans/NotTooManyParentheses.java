package com.siyeh.ipp.bool.demorgans;

class NotTooManyParentheses {
  void foo(boolean a, boolean b, boolean c) {
    if (a && (b ||<caret> c//cooment inside expr
             == c)) {}
  }
}