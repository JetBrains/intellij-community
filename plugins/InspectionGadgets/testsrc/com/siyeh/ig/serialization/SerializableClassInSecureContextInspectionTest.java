package com.siyeh.ig.serialization;

import com.siyeh.ig.IGInspectionTestCase;
import com.siyeh.ig.security.SerializableClassInSecureContextInspection;

public class SerializableClassInSecureContextInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final SerializableClassInSecureContextInspection tool = new SerializableClassInSecureContextInspection();
    tool.ignoreThrowable = true;
    doTest("com/siyeh/igtest/serialization/serializable_class_in_secure_context", tool);
  }
}