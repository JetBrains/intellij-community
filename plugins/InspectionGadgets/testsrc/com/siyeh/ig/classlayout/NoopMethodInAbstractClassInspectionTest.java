package com.siyeh.ig.classlayout;

import com.siyeh.ig.IGInspectionTestCase;

public class NoopMethodInAbstractClassInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/classlayout/noop_method_in_abstract_class", new NoopMethodInAbstractClassInspection());
  }
}