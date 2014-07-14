package com.siyeh.ipp.asserttoif.if_to_assert;

class Parentheses {
  void s(String s) {
    if<caret> (s == null) {
      throw (new NullPointerException(("s")));
    }
  }
}