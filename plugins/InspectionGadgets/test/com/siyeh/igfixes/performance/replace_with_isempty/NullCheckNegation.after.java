package com.siyeh.igfixes.performance.replace_with_isempty;

public class NullCheckNegation {

  void foo(String s) {
    if (s == null || !s.isEmpty()) {}
  }
}
