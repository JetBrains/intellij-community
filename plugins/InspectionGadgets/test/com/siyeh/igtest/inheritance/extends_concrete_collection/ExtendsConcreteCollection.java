package com.siyeh.igtest.inheritance.extends_concrete_collection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

class <warning descr="Class 'ExtendsConcreteCollection' explicitly extends 'java.util.ArrayList'">ExtendsConcreteCollection</warning> extends ArrayList {

}
class MyMap extends LinkedHashMap<String, String> {
  @Override
  protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
    return true;
  }
}