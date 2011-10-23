package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class ObjectEqualityInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final ObjectEqualityInspection tool = new ObjectEqualityInspection();
    tool.m_ignoreClassObjects = true;
    tool.m_ignorePrivateConstructors = true;
    doTest("com/siyeh/igtest/bugs/object_equality", tool);
  }
}