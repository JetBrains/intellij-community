package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class SubtractionInCompareToInspectionTest extends LightJavaInspectionTestCase {

  public void testSubtractionInCompareTo() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SubtractionInCompareToInspection();
  }
}