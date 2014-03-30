package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class Long {
  void x(long l) {
    <caret>if (l == 1) {
    } else if (l == 2) {
    } else if (l == 3) {
    } else if (l == 4) {}
  }
}