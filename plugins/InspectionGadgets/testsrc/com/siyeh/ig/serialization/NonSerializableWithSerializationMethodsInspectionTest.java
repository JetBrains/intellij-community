package com.siyeh.ig.serialization;

import com.siyeh.ig.IGInspectionTestCase;

public class NonSerializableWithSerializationMethodsInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/serialization/non_serializable_with_serialization_methods",
           new NonSerializableWithSerializationMethodsInspection());
  }
}