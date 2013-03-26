package com.siyeh.ipp.expression.flip_expression;

public class Polyadic {
  int x() {
    return 1 - 2 <caret>- 3;
  }

}