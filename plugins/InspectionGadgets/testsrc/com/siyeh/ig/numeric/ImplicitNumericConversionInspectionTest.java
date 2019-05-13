package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ImplicitNumericConversionInspectionTest extends LightInspectionTestCase {

  public void testImplicitNumericConversion() {
    doTest();
  }

  public void testIgnoreCharConversion() {
    final ImplicitNumericConversionInspection inspection = new ImplicitNumericConversionInspection();
    inspection.ignoreCharConversions = true;
    myFixture.enableInspections(inspection);
    doTest();
  }

  public void testIgnoreWidening() {
    final ImplicitNumericConversionInspection inspection = new ImplicitNumericConversionInspection();
    inspection.ignoreWideningConversions = true;
    inspection.ignoreConstantConversions = true;
    myFixture.enableInspections(inspection);
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ImplicitNumericConversionInspection();
  }
}