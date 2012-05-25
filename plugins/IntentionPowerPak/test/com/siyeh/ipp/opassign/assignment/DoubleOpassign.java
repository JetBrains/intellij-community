package com.siyeh.ipp.opassign.assignment;

class DoubleOpassign {

  void foo(double d) {
    d <caret>+= 0.5;
  }
}