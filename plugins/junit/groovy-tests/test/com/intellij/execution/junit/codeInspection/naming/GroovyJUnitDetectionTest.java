// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection.naming;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testIntegration.TestFramework;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyJUnitDetectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().addClass("package org.junit; public @interface Test {}");
  }

  @Test
  public void testJUnitTestCaseInDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("FooTest.groovy", """
      import org.junit.Test
      
      class FooTest {
          @Test
          void testFoo2() {
          }
      }""");
    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> ReadAction.runBlocking(() -> {
      PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
      TestFramework framework = TestFrameworks.detectFramework(aClass);
      assertNotNull(framework);
      assertTrue(framework.isTestClass(aClass));
    }));
  }
}
