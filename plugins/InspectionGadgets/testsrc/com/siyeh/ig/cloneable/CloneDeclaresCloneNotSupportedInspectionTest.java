package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class CloneDeclaresCloneNotSupportedInspectionTest extends LightInspectionTestCase {

  public void testCloneDeclaresCloneNonSupportedException() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new CloneDeclaresCloneNotSupportedInspection();
  }
}