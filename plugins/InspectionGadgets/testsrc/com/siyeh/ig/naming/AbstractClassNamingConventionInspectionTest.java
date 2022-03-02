// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.junit.TestClassNamingConvention;
import com.siyeh.ig.junit.TestSuiteNamingConvention;

/**
 * @author Bas Leijdekkers
 */
public class AbstractClassNamingConventionInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() { doTest(); }

  public void testSuiteNameConventionTest() {
    addEnvironmentClass("package org.junit.runner;\n" +
                        "public @interface RunWith {Class<?> value();}");
    addEnvironmentClass("package org.junit.runners;\n" +
                        "public class Suite {}");
    addEnvironmentClass("package org.junit.runners;\n" +
                        "public class Parameterized extends Suite {}");
    addEnvironmentClass("package org.junit;\n" +
                        "public @interface Test{}");
    doTest();
  }
  
  public void testNested() {
    addEnvironmentClass("package org.junit.platform.commons.annotation; @interface Testable{}");
    addEnvironmentClass("package org.junit.jupiter.api; public @interface Nested{}");
    addEnvironmentClass("package org.junit.jupiter.api; import org.junit.platform.commons.annotation.Testable; @Testable public @interface Test{}");
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    NewClassNamingConventionInspection conventionInspection = new NewClassNamingConventionInspection();
    conventionInspection.setEnabled(true, AbstractClassNamingConvention.ABSTRACT_CLASS_NAMING_CONVENTION_SHORT_NAME);
    conventionInspection.setEnabled(true, TestClassNamingConvention.TEST_CLASS_NAMING_CONVENTION_SHORT_NAME);
    conventionInspection.setEnabled(true, TestSuiteNamingConvention.TEST_SUITE_NAMING_CONVENTION_SHORT_NAME);
    return conventionInspection;
  }

  @Override
  protected Class<? extends InspectionProfileEntry> getInspectionClass() {
    return NewClassNamingConventionInspection.class;
  }
}
