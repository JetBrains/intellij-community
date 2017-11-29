package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class OverlyStrongTypeCastInspectionTest extends LightInspectionTestCase {

  public void testOverlyStrongTypeCast() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final OverlyStrongTypeCastInspection inspection = new OverlyStrongTypeCastInspection();
    inspection.ignoreInMatchingInstanceof = true;
    return inspection;
  }
}