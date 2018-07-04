package com.siyeh.igtest.inheritance.type_parameter_extends_final_class;

import java.util.*;


public class TypeParameterExtendsFinalClass<<warning descr="Type parameter 'T' extends 'final' class 'String'">T</warning> extends String> {}
final class Usee {}

class User {
  List<<warning descr="Wildcard type argument '?' extends 'final' class 'Usee'">?</warning> extends Usee> list;
  List<? extends List> l;
  private static final Collection<? extends Class> ourStopSearch = Collections.singleton(String.class);
  Collection<<warning descr="Wildcard type argument '?' extends 'final' class 'FieldIdentifier'">?</warning> extends FieldIdentifier<String>> a = Collections.singleton(new FieldIdentifier<String>());
  Collection<? extends FieldIdentifier<?>> b = Collections.singleton(new FieldIdentifier<String>());
  static final class FieldIdentifier<T> {}
}
abstract class MyList implements List<Integer> {
  @Override
  public boolean addAll(Collection<? extends Integer> c) {
    return false;
  }
}
abstract class  SampleMap<<warning descr="Type parameter 'T' extends 'final' class 'String'">T</warning> extends String> implements Map<String, Object> {

  public void putAll(final Map<? extends String, ?> m) {
    final Set<? extends Entry<? extends String,?>> entries = m.entrySet();
    for (Entry<? extends String, ?> entry : entries) {
    }
  }
}
class XXX {
  void x(List<Map.Entry<String, Object>> list) {
    for (Map.Entry<<warning descr="Wildcard type argument '?' extends 'final' class 'String'">?</warning> extends String, Object> e : list) {}
  }

  void y(Map<<warning descr="Wildcard type argument '?' extends 'final' class 'String'">?</warning> extends String, ?> m) {
    for (Map.Entry<? extends String, ?> entry : m.entrySet()) {}
  }
}