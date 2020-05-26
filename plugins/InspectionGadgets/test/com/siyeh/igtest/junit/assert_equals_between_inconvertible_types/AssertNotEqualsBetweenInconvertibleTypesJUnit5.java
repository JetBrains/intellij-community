package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MyTest {
  @org.junit.jupiter.api.Test
  void myTest() {
    <warning descr="Assertion never fails. Redundant assertion: incompatible types are compared 'String' and 'int'">assertNotEquals</warning>("java", 1, "message");
    <warning descr="Assertion never fails. Redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotEquals</warning>(new int[0], 1.0, "message");
    assertNotEquals(new int[0], new int[1]); //ok
  }
}