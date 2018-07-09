package com.siyeh.ipp.bool.negate_conditional;

class Simple {

  void f(boolean z, boolean b) {
    if (!(z<caret> ? //keep comment
          a(/*inside operand*/) : b)) {

    }

    boolean a() {
      return false;
    }
  }
}