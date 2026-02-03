// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.covertToStatic;

import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.convertToStatic.ConvertToStaticProcessor;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class ConvertTest extends LightGroovyTestCase {
  private void doTest() {
    final String testName = getTestName(true);
    final GroovyFile file = (GroovyFile)myFixture.configureByFile(testName + ".groovy");
    new ConvertToStaticProcessor(getProject(), file).run();
    myFixture.checkResultByFile(testName + "_after.groovy");
  }

  private void doIntentionTest() {
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".groovy");
    myFixture.launchAction(myFixture.findSingleIntention(GroovyRefactoringBundle.message("intention.converting.to.static")));
    myFixture.checkResultByFile(testName + "_after.groovy");
  }

  public void testIgnoreMetaprogramming() {
    doTest();
  }

  public void testAddSkippedTypes() {
    doTest();
  }

  public void testAddNecessaryConverts() {
    doTest();
  }

  public void testSpreadArguments() {
    doTest();
  }

  public void testConvertGString() {
    doTest();
  }

  public void testMultiAssignment() {
    doTest();
  }

  public void testMarkupPrintScript() {
    doTest();
  }

  public void testDontAddAnnotationTwice() {
    doTest();
  }

  public void testCompileDynamicClass() {
    doTest();
  }

  public void testNestedClasses() {
    doTest();
  }

  public void testIntentionOnUnresolvedRefs() {
    doIntentionTest();
  }

  public void testIntentionOnUnresolvedWithMethodQualifier() {
    doIntentionTest();
  }

  public void testIntentionOnProperties() {
    doIntentionTest();
  }

  public void testIntentionNecessaryConverts() {
    doIntentionTest();
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/convertToStatic";
  }
}
