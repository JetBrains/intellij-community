package com.siyeh.ipp.equality.replace_equality_with_safe_equals;

public class PrimitiveComparison {

  boolean a(int i, int j) {
    return i <caret>== j;
  }
}