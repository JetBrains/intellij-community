package com.siyeh.ipp.equality.replace_equality_with_safe_equals;

public class EnumComparison {

  enum E { A, B }

  boolean a(E a, E b) {
    return a ==<caret> b;
  }
}