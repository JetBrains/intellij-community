package com.siyeh.ig.inheritance;

import com.siyeh.ig.IGInspectionTestCase;

public class InterfaceNeverImplementedInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/inheritance/interface_never_implemented", new InterfaceNeverImplementedInspection());
  }
}
