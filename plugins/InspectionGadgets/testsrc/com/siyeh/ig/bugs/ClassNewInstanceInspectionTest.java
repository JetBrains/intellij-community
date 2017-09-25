package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ClassNewInstanceInspectionTest extends LightInspectionTestCase {

  public void testClassNewInstance() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ClassNewInstanceInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.lang;" +
      "public class InstantiationException extends Exception {}"
    };
  }
}