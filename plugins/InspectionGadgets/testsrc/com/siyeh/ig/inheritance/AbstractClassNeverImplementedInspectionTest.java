package com.siyeh.ig.inheritance;

import com.siyeh.ig.IGInspectionTestCase;

public class AbstractClassNeverImplementedInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/inheritance/abstract_class_never_implemented", new AbstractClassNeverImplementedInspection());
  }
}
