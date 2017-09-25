package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class SuspiciousArrayCastInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/bugs/suspicious_array_cast", new SuspiciousArrayCastInspection());
  }
}