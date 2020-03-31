package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class AmbiguousFieldAccessInspectionTest extends LightJavaInspectionTestCase {

  public void testAmbiguousFieldAccess() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AmbiguousFieldAccessInspection();
  }
}