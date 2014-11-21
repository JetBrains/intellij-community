package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnnecessarilyQualifiedInnerClassAccessInspectionTest extends LightInspectionTestCase {

  public void testTest() throws Exception {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessarilyQualifiedInnerClassAccessInspection();
  }
}