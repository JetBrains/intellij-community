package com.siyeh.ig.encapsulation;

import com.siyeh.ig.IGInspectionTestCase;

public class PublicFieldInspectionTest extends IGInspectionTestCase {

  public void test() {
    final PublicFieldInspection tool = new PublicFieldInspection();
    tool.ignoreEnums = true;
    tool.ignorableAnnotations.add("org.jetbrains.annotations.Nullable");
    doTest("com/siyeh/igtest/encapsulation/public_field", tool);
  }
}
