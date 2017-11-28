package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryExplicitNumericCastInspectionTest extends LightInspectionTestCase {

  public void testUnnecessaryExplicitNumericCast() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryExplicitNumericCastInspection();
  }
}