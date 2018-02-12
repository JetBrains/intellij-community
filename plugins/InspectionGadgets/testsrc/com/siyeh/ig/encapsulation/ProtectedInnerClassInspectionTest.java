package com.siyeh.ig.encapsulation;

import com.siyeh.ig.IGInspectionTestCase;

public class ProtectedInnerClassInspectionTest extends IGInspectionTestCase {

  public void test() {
    final ProtectedInnerClassInspection tool = new ProtectedInnerClassInspection();
    tool.ignoreEnums = true;
    tool.ignoreInterfaces = true;
    doTest("com/siyeh/igtest/encapsulation/protected_inner_class", tool);
  }
}
