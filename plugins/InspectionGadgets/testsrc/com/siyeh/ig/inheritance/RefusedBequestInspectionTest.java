package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class RefusedBequestInspectionTest extends LightInspectionTestCase {

  public void testRefusedBequest() { doTest(); }
  public void testCloneCallsSuperClone() { doTest(); }
  public void testSetupCallsSuperSetup() { doTest(); }
  public void testFinalizeCallsSuperFinalize() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    RefusedBequestInspection inspection = new RefusedBequestInspection();
    inspection.onlyReportWhenAnnotated = false;
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package org.junit;\n" +
      "@Retention(RetentionPolicy.RUNTIME)\n" +
      "@Target(ElementType.METHOD)\n" +
      "public @interface Before {\n" +
      "}",

      "package junit.framework;" +
      "public abstract class TestCase {" +
      "    protected void setUp() throws Exception {}" +
      "    protected void tearDown() throws Exception {}" +
      "}"
    };
  }
}
