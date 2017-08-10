package com.siyeh.ig.methodmetrics;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ThreeNegationsPerMethodInspectionTest extends LightInspectionTestCase {

  public void testThreeNegationsPerMethod() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final ThreeNegationsPerMethodInspection inspection = new ThreeNegationsPerMethodInspection();
    inspection.m_ignoreInEquals = true;
    inspection.ignoreInAssert = true;
    return inspection;
  }
}