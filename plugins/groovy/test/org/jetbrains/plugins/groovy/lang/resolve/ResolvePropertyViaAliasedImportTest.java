// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class ResolvePropertyViaAliasedImportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "resolve/imports");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.addFileToProject("com/foo/Bar.groovy", """
      package com.foo
      class Bar {
        static def myProperty = 'hello'
      }
      """);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
    myFixture.testHighlighting(LightGroovyFixtures.groovyTestName(myFixture) + ".groovy");
  }

  @Test
  public void testGetterWithAlias() {
    doTest();
  }

  @Test
  public void testGetterWithGetterAlias() {
    doTest();
  }

  @Test
  public void testGetterWithSetterAlias() {
    doTest();
  }

  @Test
  public void testPropertyWithAlias() {
    doTest();
  }

  @Test
  public void testPropertyWithGetterAlias() {
    doTest();
  }

  @Test
  public void testPropertyWithSetterAlias() {
    doTest();
  }

  @Test
  public void testSetterWithAlias() {
    doTest();
  }

  @Test
  public void testSetterWithGetterAlias() {
    doTest();
  }

  @Test
  public void testSetterWithSetterAlias() {
    doTest();
  }
}
