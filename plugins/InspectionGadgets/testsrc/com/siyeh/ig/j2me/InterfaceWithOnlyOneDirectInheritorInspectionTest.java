package com.siyeh.ig.j2me;

import com.siyeh.ig.IGInspectionTestCase;

public class InterfaceWithOnlyOneDirectInheritorInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/j2me/interface_with_only_one_direct_inheritor", new InterfaceWithOnlyOneDirectInheritorInspection());
  }
}
