package com.siyeh.ig.security;

import com.siyeh.ig.IGInspectionTestCase;

public class CloneableClassInSecureContextInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/security/cloneable_class_in_secure_context", new CloneableClassInSecureContextInspection());
  }
}