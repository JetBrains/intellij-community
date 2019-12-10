package com.siyeh.ipp.conditional.withIf;

class ConditionalInBinaryExpression {

  public String foo(int num) {
    throw num > 0 <caret>? new RuntimeException() : new IllegalStateException();
  }
}