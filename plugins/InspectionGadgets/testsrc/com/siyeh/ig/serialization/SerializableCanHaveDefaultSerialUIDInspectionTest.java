// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.serialization;

import com.siyeh.ig.IGInspectionTestCase;

public class SerializableCanHaveDefaultSerialUIDInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/serialization/serializable_can_have_default_serial_uid",
           new SerializableCanHaveDefaultSerialUIDInspection());
  }
}