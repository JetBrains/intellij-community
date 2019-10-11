package com.siyeh.ipp.conditional.withIf;

class ConditionalInBinaryExpression {

  public String foo(int num) {
      if (num > 0) thr<caret>ow new RuntimeException();
      throw new IllegalStateException();
  }
}