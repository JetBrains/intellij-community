package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryUnboxingInspectionTest extends LightInspectionTestCase {

  public void testUnnecessaryUnboxing() {
    doTest();
  }

  public void testUnnecessarySuperfluousUnboxing() {
    final UnnecessaryUnboxingInspection inspection = new UnnecessaryUnboxingInspection();
    inspection.onlyReportSuperfluouslyUnboxed = true;
    myFixture.enableInspections(inspection);
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryUnboxingInspection();
  }
}