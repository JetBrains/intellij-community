package com.siyeh.ipp.conditional.withIf;

class ConditionalInBinaryExpression {

  public String foo(int num) {
    return switch (0) {
      default -> {
          if (num > 0) yield "a";
          yield "b";
      }
    };
  }
}