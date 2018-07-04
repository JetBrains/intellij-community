package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ConstantNamingConventionInspectionTest extends LightInspectionTestCase {

  public void testConstantNamingConvention() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    FieldNamingConventionInspection inspection = new FieldNamingConventionInspection();
    inspection.setEnabled(true,new ConstantNamingConvention().getShortName());
    return inspection;
  }
}