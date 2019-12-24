package com.siyeh.ipp.conditional.withIf;

class ConditionalInBinaryExpression {

  public String foo(int num) {
      if (num > 0) {
          t<caret>hrow new RuntimeException();
      }
      throw new IllegalStateException();
  }
}