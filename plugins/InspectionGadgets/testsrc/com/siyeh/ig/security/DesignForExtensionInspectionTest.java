package com.siyeh.ig.security;

import com.siyeh.ig.IGInspectionTestCase;

public class DesignForExtensionInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/security/design_for_extension", new DesignForExtensionInspection());
  }
}