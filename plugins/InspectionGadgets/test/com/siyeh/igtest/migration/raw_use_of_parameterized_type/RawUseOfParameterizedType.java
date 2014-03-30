package com.siyeh.igtest.migration.raw_use_of_parameterized_type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@interface Anno {

  Class method() default List.class;
  Class<? extends List> method2() default List.class;
}
class RawUseOfParameterizedType {
  void array() {
    final ArrayList[] array =
      new ArrayList[10];
  }
  void anonymous() {
    new Callable() {
      @Override
      public Object call() throws Exception {
        return null;
      }
    };
  }

  void innerClass() {
    Map.Entry<String, String> entry;
  }
}
interface X {
  List foo(Map map);
}
class Y implements X {

  @Override
  public List foo(Map map) {
    return null;
  }

  boolean m(Object o) {
    final Class<List<String>[][]> aClass = (Class)List[][].class;
    return o instanceof List[];
  }

  int f(Object o) {
    return ((List[])o).length;
  }
}
