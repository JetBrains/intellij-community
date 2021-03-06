// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE.txt file.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryBoxingInspectionTest extends LightJavaInspectionTestCase {

  public void testUnnecessaryBoxing() {
    doTest();
  }

  public void testUnnecessarySuperfluousBoxing() {
    final UnnecessaryBoxingInspection inspection = new UnnecessaryBoxingInspection();
    inspection.onlyReportSuperfluouslyBoxed = true;
    myFixture.enableInspections(inspection);
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryBoxingInspection();
  }
}