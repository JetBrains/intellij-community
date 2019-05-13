package com.siyeh.ipp.opassign.operator_assignment;

class PolyadicExpression {

  void foo(int i) {
    i = i <caret>+ 1 + 2;
  }
}