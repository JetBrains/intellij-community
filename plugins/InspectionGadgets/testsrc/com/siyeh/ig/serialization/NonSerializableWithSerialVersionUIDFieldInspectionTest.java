package com.siyeh.ig.serialization;

import com.siyeh.ig.IGInspectionTestCase;

public class NonSerializableWithSerialVersionUIDFieldInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/serialization/non_serializable_with_serial_version_uid_field",
           new NonSerializableWithSerialVersionUIDFieldInspection());
  }
}