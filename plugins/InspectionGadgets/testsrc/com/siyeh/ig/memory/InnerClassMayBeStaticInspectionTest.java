package com.siyeh.ig.memory;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class InnerClassMayBeStaticInspectionTest extends LightInspectionTestCase {

  public void testInnerClassMayBeStatic() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new InnerClassMayBeStaticInspection();
  }
}