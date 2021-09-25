package com.siyeh.igtest.security.cloneable_class_in_secure_context;

class <warning descr="Class 'CloneableClassInSecureContext' may be cloned, compromising security">CloneableClassInSecureContext</warning> implements Cloneable {
}
interface EventListener<E extends Cloneable> {
}
class <warning descr="Class 'Sub' may be cloned, compromising security">Sub</warning> extends CloneableClassInSecureContext {}
class <warning descr="Class 'Data' may be cloned, compromising security">Data</warning> implements Cloneable {
  private String[] ss;

  public Data copy() {
    try {
      return (Data) clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }
}