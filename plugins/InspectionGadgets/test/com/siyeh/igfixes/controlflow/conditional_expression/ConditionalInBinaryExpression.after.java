package com.siyeh.ipp.conditional.withIf;

class ConditionalInBinaryExpression {

  public String foo(int num) {
      if (num == -1) return "string" +
              "z";
      return "string" +
              ((num == Integer.MAX_VALUE) ? "a" : "b");
  }
}