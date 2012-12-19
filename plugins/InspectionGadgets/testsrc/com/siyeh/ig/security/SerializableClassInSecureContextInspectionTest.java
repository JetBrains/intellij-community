package com.siyeh.ig.security;

import com.siyeh.ig.IGInspectionTestCase;

public class SerializableClassInSecureContextInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final SerializableClassInSecureContextInspection tool = new SerializableClassInSecureContextInspection();
    tool.ignoreThrowable = true;
    doTest("com/siyeh/igtest/security/serializable_class_in_secure_context", tool);
  }
}