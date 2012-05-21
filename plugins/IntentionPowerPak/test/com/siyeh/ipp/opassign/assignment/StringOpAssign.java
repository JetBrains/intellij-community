package com.siyeh.ipp.opassign.assignment;

class StringOpassign {

  void foo(String s) {
    s += 1<caret>.0;
  }
}