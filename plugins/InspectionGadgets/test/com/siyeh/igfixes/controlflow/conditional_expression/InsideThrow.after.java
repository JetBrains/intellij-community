package com.siyeh.ipp.conditional.withIf;

class ConditionalInBinaryExpression {

  public String foo(int num) {
      if (num > 0) {
          throw new RuntimeException();
      }<caret>
      throw new IllegalStateException();
  }
}