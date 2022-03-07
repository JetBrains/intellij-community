// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class RefusedBequestInspectionTest extends LightJavaInspectionTestCase {

  public void testRefusedBequest() { doTest(); }
  public void testCloneCallsSuperClone() { doTest(); }
  public void testSetupCallsSuperSetup() { doTest(); }
  public void testFinalizeCallsSuperFinalize() { doTest(); }
  public void testGenericsSignatures() { doTest(); }
  public void testDefaultMethods() { doTest(); }

  public void testNonTrivialSuperMethod() {
    final RefusedBequestInspection inspection = new RefusedBequestInspection();
    inspection.onlyReportWhenAnnotated = false;
    inspection.ignoreEmptySuperMethods = true;
    myFixture.enableInspections(inspection);
    doTest();
  }

  public void testSetupCallsSuperSetup2() {
    myFixture.enableInspections(new RefusedBequestInspection());
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    RefusedBequestInspection inspection = new RefusedBequestInspection();
    inspection.onlyReportWhenAnnotated = false;
    inspection.ignoreDefaultSuperMethods = true;
    return inspection;
  }

  @SuppressWarnings("RedundantThrows")
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
