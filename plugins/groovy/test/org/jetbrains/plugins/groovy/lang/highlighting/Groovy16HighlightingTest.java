// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;

@SuppressWarnings({"JUnitTestClassNamingConvention"})
public class Groovy16HighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  private void doTest(LocalInspectionTool... tools) {
    myFixture.enableInspections(tools);
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".groovy");
  }

  public void testInnerEnum() { doTest(); }

  public void testSuperWithNotEnclosingClass() { doTest(); }

  public void _testThisWithWrongQualifier() { doTest(); }

  public void testImplicitEnumCoercion1_6() { doTest(new GroovyAssignabilityCheckInspection()); }

  public void testSlashyStrings() { doTest(); }

  public void testDiamonds() { doTest(); }

  public void testStaticModifierOnToplevelDefinitionIsAllowed() {
    myFixture.configureByText("_.groovy", """
          static class A {}
          static interface I {}\s
          """);
    myFixture.checkHighlighting();
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_1_6;
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "highlighting/";
  }
}