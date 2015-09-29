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
    return new EnumeratedConstantNamingConventionInspection();
  }
}