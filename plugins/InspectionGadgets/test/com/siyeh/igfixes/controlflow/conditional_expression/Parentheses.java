package com.siyeh.ipp.conditional.withIf;

class Parentheses {
  boolean method(Object x, boolean condition, boolean y) {
    return !(<caret>condition ? x != null : y);
  }
}