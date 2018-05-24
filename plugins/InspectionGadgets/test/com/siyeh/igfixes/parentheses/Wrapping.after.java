package com.siyeh.ipp.parentheses;

class Wrapping {

  public boolean is(int value) {
    return value < 0 || value > 10
           || value != 5;
  }
}