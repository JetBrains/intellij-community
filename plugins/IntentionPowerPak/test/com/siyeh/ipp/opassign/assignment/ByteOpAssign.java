package com.siyeh.ipp.opassign.assignment;

class ByteOpassign {

  void foo(byte b) {
    b<caret> += 1;
  }
}