package com.siyeh.igtest.inheritance.type_parameter_extends_final_class;

import java.util.*;


public class TypeParameterExtendsFinalClass<<warning descr="Type parameter 'T' extends 'final' class 'String'">T</warning> extends String> {}
final class Usee {}

class User {
  List<<warning descr="Wildcard type argument '?' extends 'final' class 'Usee'">?</warning> extends Usee> list;
  List<? extends List> l;
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