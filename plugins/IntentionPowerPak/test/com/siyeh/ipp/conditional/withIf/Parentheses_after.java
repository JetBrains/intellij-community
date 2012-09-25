package com.siyeh.ipp.conditional.withIf;

class Parentheses {
  boolean method(Object x, boolean condition, boolean y) {
      if (condition) return !(x != null);
      else return !y;
  }
}