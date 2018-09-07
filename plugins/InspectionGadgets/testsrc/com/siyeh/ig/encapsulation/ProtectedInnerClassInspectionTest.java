package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ProtectedInnerClassInspectionTest extends LightInspectionTestCase {

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final ProtectedInnerClassInspection tool = new ProtectedInnerClassInspection();
    tool.ignoreEnums = true;
    tool.ignoreInterfaces = true;
    return tool;
  }

  public void testProtectedInnerClass() {
    doTest();
  }
}
