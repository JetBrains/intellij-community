// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.serialization;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.serialization.SerializableCanHaveDefaultSerialUIDInspection;

public class ChangeSerialUidToDefaultFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SerializableCanHaveDefaultSerialUIDInspection());
    myRelativePath = "serialization/changeSerialVersionUID";
    myDefaultHint = InspectionGadgetsBundle.message("inspection.serializable.can.have.default.serial.uid.fix.name");
  }

  public void testSerial() { doTest(); }
}
