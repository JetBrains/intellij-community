package com.siyeh.ipp.conditional.withIf;

class Comment {
  public String get() {
    return 239 > <caret>42 ? "239" : "42";//comment
  }
}