package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnnecessarilyQualifiedInnerClassAccessInspectionTest extends LightInspectionTestCase {

  public void testUnnecessarilyQualifiedInnerClassAccess() {
    doTest();
  }

  public void testNoImports() {
    final UnnecessarilyQualifiedInnerClassAccessInspection inspection = new UnnecessarilyQualifiedInnerClassAccessInspection();
    inspection.ignoreReferencesNeedingImport = true;
    myFixture.enableInspections(inspection);
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessarilyQualifiedInnerClassAccessInspection();
  }
}