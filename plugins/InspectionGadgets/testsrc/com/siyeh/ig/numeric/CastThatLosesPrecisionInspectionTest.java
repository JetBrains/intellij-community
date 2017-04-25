package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class CastThatLosesPrecisionInspectionTest extends LightInspectionTestCase {

  public void testCastThatLosesPrecision()  {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new CastThatLosesPrecisionInspection();
  }
}