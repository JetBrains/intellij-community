package com.siyeh.ipp.opassign.assignment;

class AssignmentAssign {
  void x(int i) {
    i <caret>+= i += 3;
  }
}