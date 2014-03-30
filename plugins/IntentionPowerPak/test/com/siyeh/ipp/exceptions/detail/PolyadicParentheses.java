package com.siyeh.ipp.exceptions.detail;

import java.io.IOException;

public class PolyadicParentheses {

  void box() {
    try {
      System.out.println(one() && (two()) && one());
    } c<caret>atch (Exception e) {}
  }

  boolean one() throws IOException {
    return false;
  }

  boolean two() throws NoSuchFieldException {
    return false;
  }
}