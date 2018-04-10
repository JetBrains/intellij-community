package com.siyeh.ipp.conditional.withIf;

class ConditionalInBinaryExpression {

  public String foo(int num) {
    return "string" +
           (num == -1<caret> ? "z" : (num == Integer.MAX_VALUE) ? "a" : "b");
  }
}