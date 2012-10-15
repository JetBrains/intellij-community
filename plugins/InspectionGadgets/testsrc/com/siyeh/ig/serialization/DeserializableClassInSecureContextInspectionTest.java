package com.siyeh.ig.serialization;

import com.siyeh.ig.IGInspectionTestCase;
import com.siyeh.ig.security.DeserializableClassInSecureContextInspection;

public class DeserializableClassInSecureContextInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final DeserializableClassInSecureContextInspection tool = new DeserializableClassInSecureContextInspection();
    tool.ignoreThrowable = true;
    doTest("com/siyeh/igtest/serialization/deserializable_class_in_secure_context", tool);
  }
}