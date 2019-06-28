package com.siyeh.ig.memory;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class InnerClassMayBeStaticInspectionTest extends LightJavaInspectionTestCase {

  public void testInnerClassMayBeStatic() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new InnerClassMayBeStaticInspection();
  }
}