package com.siyeh.ipp.opassign.operator_assignment;

class Excluded {
  void bug() {
    boolean b = true;
    b = b<caret> != false;
  }
}