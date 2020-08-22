package com.siyeh.ipp.asserttoif.if_to_assert;

import java.util.Objects;

class Parentheses {
  void s(String s) {
      Objects.requireNonNull((s), ("s"));
  }
}