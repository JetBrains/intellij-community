// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.junit.testFramework.JUnitProjectDescriptor;
import com.intellij.junit.testFramework.MavenTestLib;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testIntegration.TestFramework;
import org.jetbrains.annotations.NotNull;

public class JUnit5DetectionTest extends LightJavaCodeInsightFixtureTestCase {
  private static final LightProjectDescriptor descriptor = new JUnitProjectDescriptor(LanguageLevel.HIGHEST, MavenTestLib.JUNIT5);

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return descriptor;
  }
  
  public void testSimpleTest() {
    PsiFile file = myFixture.configureByText("A.java", """
      import org.junit.jupiter.api.Test;

      public class JavaTest {
      
          @Test
          void something() {}
      }
      """);
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertInstanceOf(framework, JupiterFramework.class);
    assertTrue(framework.isTestClass(aClass));
    PsiMethod method = aClass.getMethods()[0];
    assertTrue(framework.isTestMethod(method));
  }

  public void testFindAfterClassMethodRequiresStatic() {
    PsiFile file = myFixture.configureByText("AfterAllNonStaticTest.java", """
      import org.junit.jupiter.api.*;

      public class AfterAllNonStaticTest {
          @Test
          void something() {}

          @AfterAll
          void afterAllNonStatic() {}
      }
      """);
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertInstanceOf(framework, JupiterFramework.class);

    assertNull("findAfterClassMethod should not find non-static @AfterAll method", framework.findAfterClassMethod(aClass));
  }

  public void testSimpleTestInDumbMode() {
    PsiFile file = myFixture.configureByText("A.java", """
      import org.junit.jupiter.api.Test;

      public class JavaTest {
      
          @Test
          void something() {}
      }
      """);

    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
      TestFramework framework = TestFrameworks.detectFramework(aClass);
      assertInstanceOf(framework, JupiterFramework.class);
      assertTrue(framework.isTestClass(aClass));
      PsiMethod method = aClass.getMethods()[0];
      assertTrue(framework.isTestMethod(method));
    });
  }
}
