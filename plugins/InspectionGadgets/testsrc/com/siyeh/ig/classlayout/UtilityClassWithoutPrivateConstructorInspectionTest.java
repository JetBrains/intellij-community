package com.siyeh.ig.classlayout;

import com.siyeh.ig.IGInspectionTestCase;

public class UtilityClassWithoutPrivateConstructorInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    final UtilityClassWithoutPrivateConstructorInspection inspection =
      new UtilityClassWithoutPrivateConstructorInspection();
    inspection.ignoreClassesWithOnlyMain = true;
    doTest("com/siyeh/igtest/classlayout/utility_class_without_private_constructor",
           inspection);
  }
}