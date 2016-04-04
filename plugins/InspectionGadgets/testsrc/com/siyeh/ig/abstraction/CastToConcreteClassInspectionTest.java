package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class CastToConcreteClassInspectionTest extends LightInspectionTestCase {

  public void testCastToConcreteClass() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final CastToConcreteClassInspection inspection = new CastToConcreteClassInspection();
    inspection.ignoreInEquals = true;
    return inspection;
  }
}