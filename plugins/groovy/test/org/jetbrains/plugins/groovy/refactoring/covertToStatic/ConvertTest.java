// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.covertToStatic;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.convertToStatic.ConvertToStaticProcessor;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class ConvertTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "refactoring/convertToStatic");


  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(true);
    final GroovyFile file = (GroovyFile)myFixture.configureByFile(testName + ".groovy");
    EdtTestUtil.runInEdtAndWait(() -> {
      new ConvertToStaticProcessor(myFixture.getProject(), file).run();
      myFixture.checkResultByFile(testName + "_after.groovy");
    });
  }

  private void doIntentionTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(true);
    myFixture.configureByFile(testName + ".groovy");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(myFixture.findSingleIntention(GroovyRefactoringBundle.message("intention.converting.to.static")));
      myFixture.checkResultByFile(testName + "_after.groovy");
    });
  }

  @Test
  public void testIgnoreMetaprogramming() {
    doTest();
  }

  @Test
  public void testAddSkippedTypes() {
    doTest();
  }

  @Test
  public void testAddNecessaryConverts() {
    doTest();
  }

  @Test
  public void testSpreadArguments() {
    doTest();
  }

  @Test
  public void testConvertGString() {
    doTest();
  }

  @Test
  public void testMultiAssignment() {
    doTest();
  }

  @Test
  public void testMarkupPrintScript() {
    doTest();
  }

  @Test
  public void testDontAddAnnotationTwice() {
    doTest();
  }

  @Test
  public void testCompileDynamicClass() {
    doTest();
  }

  @Test
  public void testNestedClasses() {
    doTest();
  }

  @Test
  public void testIntentionOnUnresolvedRefs() {
    doIntentionTest();
  }

  @Test
  public void testIntentionOnUnresolvedWithMethodQualifier() {
    doIntentionTest();
  }

  @Test
  public void testIntentionOnProperties() {
    doIntentionTest();
  }

  @Test
  public void testIntentionNecessaryConverts() {
    doIntentionTest();
  }
}
