package com.siyeh.igtest.bugs.object_equality;

import java.util.*;

public class NewObjectEquality
{
  void testNew(StringBuilder s) {
    if(s == <warning descr="New object is compared using '=='">new StringBuilder()</warning>) {}
    if(<warning descr="New object is compared using '=='">new StringBuilder()</warning> == s) {}
    StringBuilder s1 = new StringBuilder();
    if(<warning descr="New object is compared using '=='">s1</warning> == s) {}
  }

  void testInferredContract(Foo foo, List<?> c) {
    if(foo == <warning descr="New object is compared using '=='">Foo.create()</warning>) {}
    if(c == <warning descr="New object is compared using '=='">Collections.unmodifiableList(c)</warning>) {}
  }
}
class Foo {
  static Foo create() {
    return new Foo();
  }
}