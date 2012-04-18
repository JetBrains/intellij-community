package com.siyeh.ig.classlayout;

import com.siyeh.ig.IGInspectionTestCase;

public class EmptyClassInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final EmptyClassInspection tool = new EmptyClassInspection();
    tool.ignoreClassWithParameterization = true;
    tool.ignoreThrowables = true;
    doTest("com/siyeh/igtest/classlayout/emptyclass", tool);
  }
}