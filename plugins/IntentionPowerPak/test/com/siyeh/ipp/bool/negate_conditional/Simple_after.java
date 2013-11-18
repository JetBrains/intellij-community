package com.siyeh.ipp.bool.negate_conditional;

class Simple {

  void f(boolean z, boolean a, boolean b) {
    if (z ? !a : !b) {

    }
  }
}