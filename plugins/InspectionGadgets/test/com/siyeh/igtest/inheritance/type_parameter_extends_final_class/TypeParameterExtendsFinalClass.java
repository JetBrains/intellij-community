package com.siyeh.igtest.inheritance.type_parameter_extends_final_class;

import java.util.Collection;
import java.util.List;

public class TypeParameterExtendsFinalClass<T extends String> {}
final class Usee {}

class User {
  List<? extends Usee> list;
  List<? extends List> l;
}
abstract class MyList implements List<Integer> {
  @Override
  public boolean addAll(Collection<? extends Integer> c) {
    return false;
  }
}