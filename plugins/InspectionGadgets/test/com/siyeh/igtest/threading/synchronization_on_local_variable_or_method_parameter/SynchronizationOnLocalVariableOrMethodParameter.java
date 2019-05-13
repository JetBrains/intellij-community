package com.siyeh.igtest.threading.synchronization_on_local_variable_or_method_parameter;

import java.util.*;

class SynchronizationOnLocalVariableOrMethodParameter {

  static {
    final Object lock = new Object();
    new Object(){{
      synchronized (lock) {} // no warning
    }};
  }

  interface IntegerMath {
    int operation(int a, int b);
  }
  public int operateBinary(int a, int b, IntegerMath op) {
    return op.operation(a, b);
  }
  public static void foo() {
    final Object lock = new Object();
    final SynchronizationOnLocalVariableOrMethodParameter x = new SynchronizationOnLocalVariableOrMethodParameter();
    IntegerMath addition = (a, b) -> {
      synchronized(lock) {return a + b;} // no warning
    };
    System.out.println("40 + 2 = " +
                       x.operateBinary(40, 2, addition));
  }

  void bar() {
    final Object lock = new Object();
    synchronized (<warning descr="Synchronization on local variable 'lock'">lock</warning>) {

    }
  }

  void bar2() {
    Object lock = new Object();
    synchronized (((<warning descr="Synchronization on local variable 'lock'">lock</warning>))) {
      System.out.println(lock);
    }
  }

  public void error(List<Object> foo) {
    List<Object> bar = Collections.synchronizedList(foo);
    synchronized (bar) {
      for (Object o : bar) {
        // ...
      }
    }
  }

  public void error2(List<Object> foo) {
    List<Object> bar;
    bar = Collections.synchronizedList(foo);
    synchronized (bar) {
      for (Object o : bar) {
        // ...
      }
    }
  }

  void concurrent() {
    final Object o = new Object();
    synchronized (o) {
      System.out.println(o.toString());
    }
    new Thread() {
      public void run() {
        for (int i = 0; i < 10000000; i++) {
          synchronized (o) {
            System.out.println(o.toString());
          }
        }
      }
    }.start();
  }
}