package com.siyeh.igtest.bugs.object_equality;

import java.util.*;

public class ControlFlow
{
  void nonLeak(Object obj) {
    while (true) {
      Object obj2 = new Object();
      if (obj == <warning descr="New object is compared using '=='">obj2</warning>) {
        System.out.println("equal");
      } else {
        if (Math.random() > 0.5) return;
      }
    }
  }

  void test() {
    while (true) {
      Object obj = new Object();
      Object obj2 = getObject(obj);
      if (obj == obj2) {
        System.out.println("equal");
      }
    }
  }

  void test1() {
    Object obj = new Object();
    Object obj2 = getObject(obj);
    if (obj == obj2) {
      System.out.println("equal: "+obj+":"+obj2);
    }
  }
  
  void test2() {
    Object obj = new Object();
    Object obj2 = getObject(null);
    if (<warning descr="New object is compared using '=='">obj</warning> == obj2) {
      System.out.println("equal: "+obj+":"+obj2);
    }
  }
  
  void outerScope() {
    Object obj = new Object();
    java.util.function.Consumer<Object> r = x -> {
      Object obj2 = getObject(x);
      if (obj == obj2) {
        System.out.println("equal: "+obj+":"+obj2);
      }
    };
    r.accept(obj);
  }
  
  {
    Object obj = new Object();
    {
      Object obj2 = getObject(null);
      if (<warning descr="New object is compared using '=='">obj</warning> == obj2) {
        System.out.println("equal: "+obj+":"+obj2);
      }
    }
  }

  void usedInSameExpression() {
    Object obj = new Object();
    if (obj == getObject(obj)) {}
  }
  
  private Object getObject(Object obj) {
    return Math.random() > 0.5 ? obj : new Object();
  }
}