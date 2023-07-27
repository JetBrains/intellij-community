// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.migration;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.UnnecessaryUnboxingInspection;

public class UnnecessaryUnboxingFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnnecessaryUnboxingInspection());
  }

  public void testBooleanObject() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.unboxing.remove.quickfix", "Boolean.TRUE"),
                 "Boolean getBoolean(Boolean object) {" +
                 "  return object.booleanValue/**/();" +
                 "}",
                 "Boolean getBoolean(Boolean object) {" +
                 "  return object;" +
                 "}");
  }
}