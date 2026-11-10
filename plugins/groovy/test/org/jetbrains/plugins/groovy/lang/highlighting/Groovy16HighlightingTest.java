// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@SuppressWarnings({"JUnitTestClassNamingConvention"})
@TestApplication
public class Groovy16HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_1_6);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "highlighting/");
  }

  private void doTest(LocalInspectionTool... tools) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(tools);
    myFixture.testHighlighting(true, false, false, myFixture.getTestName(false) + ".groovy");
  }

  @Test
  public void testInnerEnum() { doTest(); }

  @Test
  public void testSuperWithNotEnclosingClass() { doTest(); }

  public void _testThisWithWrongQualifier() { doTest(); }

  @Test
  public void testImplicitEnumCoercion1_6() { doTest(new GroovyAssignabilityCheckInspection()); }

  @Test
  public void testSlashyStrings() { doTest(); }

  @Test
  public void testDiamonds() { doTest(); }

  @Test
  public void testStaticModifierOnToplevelDefinitionIsAllowed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
          static class A {}
          static interface I {}\s
          """);
    myFixture.checkHighlighting();
  }
}