// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.junit;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.junit.JUnit4AnnotatedMethodInJUnit3TestCaseInspection;

public class RemoveIgnoreAndRenameFixTest extends IGQuickFixesTestCase {
  public void testRemoveIgnoreAndRename() {
    doTest(InspectionGadgetsBundle.message("ignore.test.method.in.class.extending.junit3.testcase.quickfix", "_test" + getTestName(false)));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new JUnit4AnnotatedMethodInJUnit3TestCaseInspection());
    myRelativePath = "junit/remove_ignore_and_rename";

    myFixture.addClass("""
                         package org.junit;
                         public @interface Ignore {}""");

    myFixture.addClass("""
                         package junit.framework;
                         public abstract class TestCase extends Assert, Test {
                           @Override
                           public int countTestCases() {
                             return 1;
                           }
                           @Override
                           public void run(TestResult result) {}
                         }""");
  }
}
