package com.siyeh.ig.serialization;

import com.siyeh.ig.IGInspectionTestCase;

public class SerializableHasSerialVersionUIDFieldInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/serialization/serializable_has_serial_version_uid_field",
           new SerializableHasSerialVersionUIDFieldInspection());
  }
}