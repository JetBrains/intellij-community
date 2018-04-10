package com.siyeh.ipp.conditional.withIf;

class Comment {
  public String get() {
    return 239 > <caret>42 ? /*before then*/"239"/*after then*/ : "42";//comment
  }
}