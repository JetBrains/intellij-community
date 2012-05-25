package com.siyeh.ipp.opassign.assignment;

class ByteOpassign {

  void foo(byte b) {
      b = (byte)(b + 1);
  }
}