package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class MethodMayBeStaticInspectionTest extends LightInspectionTestCase {

  public void testA() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final MethodMayBeStaticInspection inspection = new MethodMayBeStaticInspection();
    inspection.m_ignoreEmptyMethods = false;
    inspection.m_ignoreDefaultMethods = false;
    return inspection;
  }
}