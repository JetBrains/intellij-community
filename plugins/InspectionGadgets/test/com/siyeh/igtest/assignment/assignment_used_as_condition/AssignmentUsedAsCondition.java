package com.siyeh.igtest.assignment.assignment_used_as_condition;

public class AssignmentUsedAsCondition {

  void foo(boolean b) {
    if (b = fossa()) {

    }
  }

  boolean fossa() {
    return true;
  }

  public static void main(String[] args) {
    boolean b = false;
    if (b != = true) {

    }
    int i = 1;
    if (i = 8);
  }
}