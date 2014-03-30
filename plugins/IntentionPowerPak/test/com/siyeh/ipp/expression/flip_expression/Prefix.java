package com.siyeh.ipp.expression.flip_expression;

class Prefix {
  int x() {
    return 1 <caret>- -1;
  }
}