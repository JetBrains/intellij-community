package com.siyeh.ig.security;

import com.siyeh.ig.IGInspectionTestCase;

public class DeserializableClassInSecureContextInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final DeserializableClassInSecureContextInspection tool = new DeserializableClassInSecureContextInspection();
    tool.ignoreThrowable = true;
    doTest("com/siyeh/igtest/security/deserializable_class_in_secure_context", tool);
  }
}