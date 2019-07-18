package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class MismatchedStringBuilderQueryUpdateInspectionTest extends LightJavaInspectionTestCase {

  public void testMismatchedStringBuilderQueryUpdate() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new MismatchedStringBuilderQueryUpdateInspection();
  }
}