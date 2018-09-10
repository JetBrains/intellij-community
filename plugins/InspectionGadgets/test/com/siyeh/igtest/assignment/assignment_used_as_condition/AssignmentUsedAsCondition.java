package com.siyeh.igtest.assignment.assignment_used_as_condition;

public class AssignmentUsedAsCondition {

  void foo(boolean b) {
    if (<warning descr="'b = fossa()' used as condition">b = fossa()</warning>) {
    }
    if ((<warning descr="'b = fossa()' used as condition">b = fossa()</warning>)) {}
  }

  boolean fossa() {
    return true;
  }

  public static void main(String[] args) {
    boolean b = false;
    if (b !=<error descr="Expression expected"> </error>= true) {

    }
    int i = 1;
    if (<error descr="Incompatible types. Found: 'int', required: 'boolean'"><warning descr="'i = 8' used as condition">i = 8</warning></error>);
  }
}