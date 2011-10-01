package com.siyeh.ig.migration;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryBoxingInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/migration/unnecessary_boxing",
           new UnnecessaryBoxingInspection());
  }
}