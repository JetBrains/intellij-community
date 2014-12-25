package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ImplicitNumericConversionInspectionTest extends LightInspectionTestCase {

  public void testImplicitNumericConversion() throws Exception {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ImplicitNumericConversionInspection();
  }
}