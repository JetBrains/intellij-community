package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class EmptyClassInspectionTest extends LightJavaInspectionTestCase {

  public void testEmptyClass() {
    doTest();
  }

  public void testPackageInfo() {
    doNamedTest("package-info");
  }

  public void testEmptyFile() {
    doTest();
  }

  public void testClassWithComments() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final EmptyClassInspection inspection = new EmptyClassInspection();
    inspection.ignoreClassWithParameterization = true;
    inspection.ignoreThrowables = true;
    inspection.commentsAreContent = true;
    return inspection;
  }
}