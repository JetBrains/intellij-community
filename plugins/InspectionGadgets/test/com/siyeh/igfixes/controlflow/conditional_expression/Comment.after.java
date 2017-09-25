package com.siyeh.ipp.conditional.withIf;

class Comment {
  public String get() {
      if (239 > 42) return "239";//comment
      else return "42";//comment
  }
}