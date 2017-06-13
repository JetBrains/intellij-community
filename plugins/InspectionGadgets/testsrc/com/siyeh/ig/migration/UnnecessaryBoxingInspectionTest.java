// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryBoxingInspectionTest extends LightInspectionTestCase {

  public void testUnnecessaryBoxing() {
    doTest();
  }

  public void testUnnecessarySuperfluousBoxing() {
    final UnnecessaryBoxingInspection inspection = new UnnecessaryBoxingInspection();
    inspection.onlyReportSuperfluouslyBoxed = true;
    myFixture.enableInspections(inspection);
    doTest();
  }

  Integer foo2(String foo, int bar) {
    return foo == null ? 0 : bar;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryBoxingInspection();
  }
}