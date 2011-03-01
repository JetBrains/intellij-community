package com.siyeh.ig.jdk;

import com.siyeh.ig.IGInspectionTestCase;

public class AutoUnboxingInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/jdk/auto_unboxing", new AutoUnboxingInspection());
  }
}