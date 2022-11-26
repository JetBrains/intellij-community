// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.junit;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.junit.JUnit4AnnotatedMethodInJUnit3TestCaseInspection;

public class RemoveTestAnnotationFixTest extends IGQuickFixesTestCase {
  public void testRemoveAnnotationAndRename() {
    doTest(InspectionGadgetsBundle.message("remove.junit4.test.annotation.and.rename.quickfix", "test" + getTestName(false)));
  }

  public void testRemoveTestAnnotation() {
    doTest(InspectionGadgetsBundle.message("remove.junit4.test.annotation.quickfix"));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new JUnit4AnnotatedMethodInJUnit3TestCaseInspection());
    myRelativePath = "junit/remove_test_annotation";

    myFixture.addClass("""
                         package org.junit;
                         public @interface Test {}""");

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
