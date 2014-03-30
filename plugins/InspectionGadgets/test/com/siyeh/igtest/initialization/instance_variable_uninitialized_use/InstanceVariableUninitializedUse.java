package com.siyeh.igtest.initialization.instance_variable_uninitialized_use;

import java.io.IOException;




class InstanceVariableUnitializedUse {

  int i;
  InstanceVariableUnitializedUse() throws IOException {

    try (java.io.FileInputStream in = new java.io.FileInputStream("asdf" + (i=3) + "asdf")) {}
    System.out.println(i);

  }
}
class InstanceFieldVsDoWhile {

  private Object object;

  public InstanceFieldVsDoWhile() {
    do {
      object = new Object();
    } while (object.hashCode() < 1000); // Instance field used before initialization
  }
}
class FinalField {
  private final Object object;

  FinalField() {
    System.out.println(object);
    object = null;
  }
}
