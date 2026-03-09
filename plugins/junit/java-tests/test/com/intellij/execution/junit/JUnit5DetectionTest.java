// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testIntegration.TestFramework;

public class JUnit5DetectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit.platform.commons.annotation; public @interface Testable {}");
    myFixture.addClass("""
    package org.junit.jupiter.api;

    import org.junit.platform.commons.annotation.Testable;

    @Testable
    public @interface Test {}
    """);
    myFixture.addClass("package org.junit.jupiter.api; public @interface BeforeAll {}");
    myFixture.addClass("package org.junit.jupiter.api; public @interface AfterAll {}");
    myFixture.addClass("package org.junit.jupiter.api; public @interface BeforeEach {}");
    myFixture.addClass("package org.junit.jupiter.api; public @interface AfterEach {}");
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
    assertNotNull(framework);
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
    assertNotNull(framework);

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
      assertNotNull(framework);
      assertTrue(framework.isTestClass(aClass));
      PsiMethod method = aClass.getMethods()[0];
      assertTrue(framework.isTestMethod(method));
    });
  }
}
