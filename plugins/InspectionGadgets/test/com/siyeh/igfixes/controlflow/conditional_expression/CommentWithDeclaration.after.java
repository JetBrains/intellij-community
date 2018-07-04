package com.siyeh.ipp.conditional.withIf;

class Comment {
  public String get() {
    String s;//comment
      /*before then*/
      /*after then*/
      if (239 > 42) s = "239";
      else s = "42";
      return s;
  }
}