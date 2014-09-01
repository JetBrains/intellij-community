package com.siyeh.ipp.equality.replace_equality_with_safe_equals;

public class SimpleObjectComparison {

  boolean a(Object a, Object b) {
    return a == null ? b == null : a.equals(b);
  }
}