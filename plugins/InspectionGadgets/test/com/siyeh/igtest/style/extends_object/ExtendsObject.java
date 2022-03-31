package com.siyeh.igtest.controlflow.extends_object;

public class ExtendsObject extends <warning descr="Class 'ExtendsObject' explicitly extends 'java.lang.Object'">Object</warning> {
public class InnerClass extends <warning descr="Class 'InnerClass' explicitly extends 'java.lang.Object'">java.lang.Object</warning> {
  }
public class InheritedClass extends java.util.Date {
  }
}
