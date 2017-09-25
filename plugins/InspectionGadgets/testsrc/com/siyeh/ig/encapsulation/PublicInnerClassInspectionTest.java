package com.siyeh.ig.encapsulation;

import com.siyeh.ig.IGInspectionTestCase;

public class PublicInnerClassInspectionTest extends IGInspectionTestCase {

  public void test() {
    final PublicInnerClassInspection tool = new PublicInnerClassInspection();
    tool.ignoreEnums = true;
    tool.ignoreInterfaces = true;
    doTest("com/siyeh/igtest/encapsulation/public_inner_class", tool);
  }
}
