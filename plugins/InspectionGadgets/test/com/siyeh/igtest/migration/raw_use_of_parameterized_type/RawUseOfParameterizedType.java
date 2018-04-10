package com.siyeh.igtest.migration.raw_use_of_parameterized_type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@interface Anno {

  <warning descr="Raw use of parameterized class 'Class'">Class</warning> method() default List.class;
  Class<? extends List> method2() default List.class;
}
class RawUseOfParameterizedType {
  void array() {
    final <warning descr="Raw use of parameterized class 'ArrayList'">ArrayList</warning>[] array =
      new ArrayList[10];
  }
  void anonymous() {
    new <warning descr="Raw use of parameterized class 'Callable'">Callable</warning>() {
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
  <warning descr="Raw use of parameterized class 'List'">List</warning> foo(<warning descr="Raw use of parameterized class 'Map'">Map</warning> map);
}
class Y implements X {

  Y(Map<String, <warning descr="Raw use of parameterized class 'Comparable'">Comparable</warning>> properties) {}

  @Override
  public <warning descr="Raw use of parameterized class 'List'">List</warning> foo(Map map) {
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
class Z extends Y {
  Z(Map<String, Comparable> properties) {
    this(properties, "");
  }

  Z(Map<String, Comparable> properties, String s) {
    super(properties);
  }
}
