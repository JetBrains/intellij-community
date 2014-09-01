package com.siyeh.ipp.equality.replace_equality_with_safe_equals;

public class NullComparison {

  boolean a(Object a) {
    return a ==<caret> null;
  }
}