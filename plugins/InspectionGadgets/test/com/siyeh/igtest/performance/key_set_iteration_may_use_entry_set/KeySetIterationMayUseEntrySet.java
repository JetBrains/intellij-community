package com.siyeh.igtest.performance.key_set_iteration_may_use_entry_set;

import java.util.Map;


public class KeySetIterationMayUseEntrySet {

  void foo(Map<String, String> m) {
    for (String k : m.keySet()) {
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
