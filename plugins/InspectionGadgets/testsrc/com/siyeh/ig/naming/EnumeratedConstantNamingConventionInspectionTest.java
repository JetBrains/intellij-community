package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class EnumeratedConstantNamingConventionInspectionTest extends LightInspectionTestCase {

  public void testEnumeratedConstantNamingConvention() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    FieldNamingConventionInspection inspection = new FieldNamingConventionInspection();
    inspection.setEnabled(true, new EnumeratedConstantNamingConvention().getShortName());
    return inspection;
  }
}