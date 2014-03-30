package com.siyeh.igtest.controlflow.duplicate_condition;

public class DuplicateCondition {

  void x(boolean b) {
    if (b || b || b ) {

    } else if (b) {

    } else if (b) {}
  }
}