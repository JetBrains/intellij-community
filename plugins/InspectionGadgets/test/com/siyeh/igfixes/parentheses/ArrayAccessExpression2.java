package com.siyeh.ipp.parentheses;

class ArrayAccessExpression2 {
  private static void x() {
    Object info  = new Object[]{"abc"};
    String s = (String)(((Object[])info)[0]<caret>);
  }
}