// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.junit;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.junit.JUnit4AnnotatedMethodInJUnit3TestCaseInspection;

/**
 * @author Bas Leijdekkers
 */
public class ConvertToJUnit4FixTest extends IGQuickFixesTestCase {

  public void testOtherMethods() { doTest(); }
  public void testLocalMethod() { doTest(); }
  public void testInheritance() { doTest(); }

  public void testSuiteCase() {
    final String name = getTestName(false);
    myFixture.testHighlighting(getRelativePath() + "/" + name + ".java");
    final IntentionAction action =
      myFixture.getAvailableIntention(InspectionGadgetsBundle.message("convert.junit3.test.class.quickfix", name));
    assertNotNull(action);
    try {
      myFixture.launchAction(action);
      fail("conflict expected");
    } catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Reference <b><code>new SuiteCase()</code></b> will not compile when class <b><code>SuiteCase</code></b> is converted to JUnit 4",
                   e.getMessage());
    }
  }

  @Override
  protected void doTest() {
    doTest(InspectionGadgetsBundle.message("convert.junit3.test.class.quickfix", getTestName(false)));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new JUnit4AnnotatedMethodInJUnit3TestCaseInspection());
    myRelativePath = "junit/convert_to_junit4";

    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "  public static void assertTrue(boolean condition) {}" +
                       "  public static void assertFalse(boolean condition) {}" +
                       "}");
    myFixture.addClass("package org.junit;" +
                       "@Retention(RetentionPolicy.RUNTIME)" +
                       "@Target({ElementType.METHOD})" +
                       "public @interface Test {}");
    myFixture.addClass("package junit.framework;" +
                       "public class Assert {" +
                       "  public static void assertTrue(boolean condition) {}" +
                       "  public static void assertFalse(String message, boolean condition) {}" +
                       "}");
    myFixture.addClass("""
                         package junit.framework;public interface Test {
                           int countTestCases();
                           void run(TestResult result);
                         }""");
    myFixture.addClass("""
                         package junit.framework;public abstract class TestCase extends Assert, Test {  @Override public int countTestCases() {
                             return 1;
                           }  @Override public void run(TestResult result) {}}""");
    myFixture.addClass("package junit.framework;" +
                       "public class TestSuite implements Test {" +
                       "  public void addTest(Test test) {}" +
                       "}");
  }
}
