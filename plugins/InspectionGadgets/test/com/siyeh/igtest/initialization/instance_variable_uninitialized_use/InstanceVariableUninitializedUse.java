package com.siyeh.igtest.initialization.instance_variable_uninitialized_use;

import java.io.IOException;




class InstanceVariableUnitializedUse {

  int i;
  InstanceVariableUnitializedUse() throws IOException {

    try (java.io.FileInputStream in = new java.io.FileInputStream("asdf" + (i=3) + "asdf")) {}
    System.out.println(i);

  }
}