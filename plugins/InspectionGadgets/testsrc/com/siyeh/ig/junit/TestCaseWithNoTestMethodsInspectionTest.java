// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class TestCaseWithNoTestMethodsInspectionTest extends LightJavaInspectionTestCase {

  public void testTestCaseWithNoTestMethods() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    TestCaseWithNoTestMethodsInspection inspection = new TestCaseWithNoTestMethodsInspection();
    inspection.ignoreSupers = true;
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package junit.framework;" +
      "public abstract class TestCase {" +
      "    protected void setUp() throws Exception {}" +
      "    protected void tearDown() throws Exception {}" +
      "}",
      "package junit.framework;\n" +
      "public interface Test {\n" +
      "    public abstract int countTestCases();\n" +
      "    public abstract void run(TestResult result);\n" +
      "}",
      "package org.junit; public @interface Ignore {}",
      "package org.junit.jupiter.api;" +
      "import org.junit.platform.commons.annotation.Testable;" +
      "@Testable\n" +
      "public @interface Test {}",
      "package org.junit.jupiter.api;" +
      "public @interface Nested {}",
      "package org.junit.platform.commons.annotation;" +
      "public @interface Testable {}"
    };
  }
}
