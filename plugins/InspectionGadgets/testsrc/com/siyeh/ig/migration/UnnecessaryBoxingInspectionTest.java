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

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryBoxingInspection();
  }
}