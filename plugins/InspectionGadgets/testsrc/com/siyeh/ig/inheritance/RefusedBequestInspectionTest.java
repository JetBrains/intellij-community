package com.siyeh.ig.inheritance;

import com.siyeh.ig.IGInspectionTestCase;

public class RefusedBequestInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/inheritance/refused_bequest", new RefusedBequestInspection());
  }
}
