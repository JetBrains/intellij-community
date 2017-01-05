package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MyTest {
  void myTest() {
    <warning descr="'assertEquals()' between objects of inconvertible types 'int' and 'String'">assertEquals</warning>(1, "", "error message");
    <warning descr="'assertEquals()' between objects of inconvertible types 'int' and 'String'">assertEquals</warning>(1, "", () -> "error message in supplier");
    assertEquals(1, 42, "message");
    assertEquals(1, 42, () -> "message");
  }
}