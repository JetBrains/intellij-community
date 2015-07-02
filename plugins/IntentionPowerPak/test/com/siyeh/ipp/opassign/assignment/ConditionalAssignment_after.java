package com.siyeh.ipp.opassign.assignment;

class Conditional {
  void x(int i) {
      i = i * (1 == 1 ? 2 : 3);
  }
}