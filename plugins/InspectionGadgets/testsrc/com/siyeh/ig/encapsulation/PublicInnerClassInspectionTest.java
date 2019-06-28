package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class PublicInnerClassInspectionTest extends LightJavaInspectionTestCase {
  public void testPublicInnerClass() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final PublicInnerClassInspection tool = new PublicInnerClassInspection();
    tool.ignoreEnums = true;
    tool.ignoreInterfaces = true;
    return tool;
  }
}
