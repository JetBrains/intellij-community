package com.siyeh.igtest.performance.object_allocation_in_loop;

import java.util.regex.*;
import java.util.function.*;
import java.util.*;

class ObjectAllocationInLoop {

  void m() {
    while (true) {
      new <warning descr="Object allocation 'new Object()' in loop">Object</warning>();
    }
  }

  void m1() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 10; i++) {
      if(sb != null) {
        sb.append(i);
      } else {
        sb = new StringBuilder(String.valueOf(i));
      }
    }
  }

  void m2() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 10; i++) {
      if (sb == null) {
        sb = new StringBuilder(String.valueOf(i));
      }
      else {
        sb.append(i);
      }
    }
  }

  boolean checkPatterns(String[] patterns) {
    for (String pattern : patterns) {
      try {
        Pattern.<warning descr="Object allocation via 'compile()' call in loop">compile</warning>(pattern);
      }
      catch (PatternSyntaxException exception) {
        return false;
      }
    }
    return true;
  }

  void methodRef(int[] data, String foo) {
    for(int val : data) {
      Runnable r = <warning descr="Object allocation via instance-bound method reference 'System.out::println()' in loop">System.out::println</warning>;
      Supplier<String> r1 = <warning descr="Object allocation via instance-bound method reference 'foo::trim()' in loop">foo::trim</warning>;
      Predicate<String> r2 = String::isEmpty;
      Supplier<String> r3 = String::new;
      IntFunction<String[]> r4 = String[]::new;
      Runnable r5 = <warning descr="Object allocation via instance-bound method reference 'this::foo()' in loop">this::foo</warning>;
    }
  }

  void foo() {}

  void lambda(int[] data, String foo) {
    for(int val : data) {
      Runnable r = () -> System.out.println();
      Supplier<String> r1 = <warning descr="Object allocation via capturing lambda in loop">()</warning> -> foo.trim();
      Runnable r2 = <warning descr="Object allocation via capturing lambda in loop">()</warning> -> methodRef(data, foo);
      Supplier<Object> r3 = <warning descr="Object allocation via capturing lambda in loop">()</warning> -> this;
      IntSupplier r4 = <warning descr="Object allocation via capturing lambda in loop">()</warning> -> {
        int x = 5;
        return val;
      };
      IntSupplier r5 = () -> {
        int x = 5;
        return x;
      };
    }
  }

  // IDEA-206221
  public static Map<Integer, Boolean> viaLoop(List<Boolean> values) {
    Map<Integer, Boolean> result = new HashMap<>();
    for (Boolean value : values) {
      result.merge(key(), value, (oldValue, newValue) -> Boolean.FALSE.equals(oldValue) ? newValue : oldValue);
    }
    return result;
  }

  static native Integer key();

  final int CONST = 10;
  
  void concat() {
    for (int i = 0; i < 10; i++) {
      String s = <warning descr="Object allocation via string concatenation in loop">"value: " + i</warning>;
      String s2 = "value: " + CONST;
      System.out.println(s);
      System.out.println(s2);
    }
  }
  
  void arrayInit() {
    for (int i = 0; i < 10; i++) {
      int[] i1 = <warning descr="Array allocation in loop">{0}</warning>;
      int[][] i2 = <warning descr="Array allocation in loop">{{0}}</warning>;
      int[] i3 = new <warning descr="Array allocation in loop">int</warning>[] {0};
      int[] i4 = new <warning descr="Array allocation in loop">int</warning>[10];
    }
  }
}