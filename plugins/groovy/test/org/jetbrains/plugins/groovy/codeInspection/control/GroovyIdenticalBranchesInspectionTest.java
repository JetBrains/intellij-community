// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class GroovyIdenticalBranchesInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "inspections/identicalBranches/");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.enableInspections(GroovyConditionalWithIdenticalBranchesInspection.class,
                                   GroovyIfStatementWithIdenticalBranchesInspection.class);
  }

  @Test
  public void testTwoNewExpressions() { highlighting.get().doTest(); }

  @Test
  public void testEmptyListAndMap() { highlighting.get().doTest(); }

  @Test
  public void testIdenticalArrays() { highlighting.get().doTest(); }
}
