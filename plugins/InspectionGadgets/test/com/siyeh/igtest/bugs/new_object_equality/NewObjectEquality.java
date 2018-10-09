package com.siyeh.igtest.bugs.object_equality;

import java.util.*;

public class NewObjectEquality
{
  void testNew(StringBuilder s) {
    if(s <warning descr="New object is compared using '=='">==</warning> new StringBuilder()) {}
    if(new StringBuilder() <warning descr="New object is compared using '=='">==</warning> s) {}
    StringBuilder s1 = new StringBuilder();
    if(s1 <warning descr="New object is compared using '=='">==</warning> s) {}
  }

  void testInferredContract(Foo foo, List<?> c) {
    if(foo <warning descr="New object is compared using '=='">==</warning> Foo.create()) {}
    if(c <warning descr="New object is compared using '=='">==</warning> Collections.unmodifiableList(c)) {}
  }
}
class Foo {
  static Foo create() {
    return new Foo();
  }
}