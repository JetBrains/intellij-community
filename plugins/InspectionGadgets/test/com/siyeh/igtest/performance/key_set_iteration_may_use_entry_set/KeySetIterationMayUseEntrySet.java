package com.siyeh.igtest.performance.key_set_iteration_may_use_entry_set;

import java.util.Map;


public class KeySetIterationMayUseEntrySet {

  void foo(Map<String, String> m) {
    for (String k : <warning descr="Iteration over 'm.keySet()' may be replaced with 'entrySet()' iteration">m.keySet()</warning>) {
      System.out.println(m.get(k));
    }
  }

  class MyClass {
    private Map<String, Integer> map;

    public MyClass(Map<String, Integer> map) {
      this.map = map;
    }

    public void myMethod(MyClass other) {
      for (String key : map.keySet()) {
        Integer valueFromMap2 = other.map.get(key);
      }
    }
  }
}
