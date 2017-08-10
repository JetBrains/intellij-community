package com.siyeh.ig.j2me;

import com.siyeh.ig.IGInspectionTestCase;

public class AbstractWithOnlyOneDirectInheritorInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/j2me/abstract_class_with_only_one_direct_inheritor", new AbstractClassWithOnlyOneDirectInheritorInspection());
  }
}
