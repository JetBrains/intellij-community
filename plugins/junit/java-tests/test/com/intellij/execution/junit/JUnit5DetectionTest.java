// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.junit.testFramework.JUnitProjectDescriptor;
import com.intellij.junit.testFramework.MavenTestLib;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testIntegration.TestFramework;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JUnit5DetectionTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(new JUnitProjectDescriptor(LanguageLevel.HIGHEST, MavenTestLib.JUNIT5));
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSimpleTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("A.java", """
      import org.junit.jupiter.api.Test;

      public class JavaTest {

          @Test
          void something() {}
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
      TestFramework framework = TestFrameworks.detectFramework(aClass);
      assertInstanceOf(JupiterFramework.class, framework);
      assertTrue(framework.isTestClass(aClass));
      PsiMethod method = aClass.getMethods()[0];
      assertTrue(framework.isTestMethod(method));
    });
  }

  @Test
  public void testFindAfterClassMethodRequiresStatic() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("AfterAllNonStaticTest.java", """
      import org.junit.jupiter.api.*;

      public class AfterAllNonStaticTest {
          @Test
          void something() {}

          @AfterAll
          void afterAllNonStatic() {}
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
      TestFramework framework = TestFrameworks.detectFramework(aClass);
      assertInstanceOf(JupiterFramework.class, framework);

      assertNull(framework.findAfterClassMethod(aClass), "findAfterClassMethod should not find non-static @AfterAll method");
    });
  }

  @Test
  public void testSimpleTestInDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("A.java", """
      import org.junit.jupiter.api.Test;

      public class JavaTest {

          @Test
          void something() {}
      }
      """);

    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> ReadAction.runBlocking(() -> {
      PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
      TestFramework framework = TestFrameworks.detectFramework(aClass);
      assertInstanceOf(JupiterFramework.class, framework);
      assertTrue(framework.isTestClass(aClass));
      PsiMethod method = aClass.getMethods()[0];
      assertTrue(framework.isTestMethod(method));
    }));
  }

  @Test
  public void testAbstractClassWithMetaAnnotatedExtendWith() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
      import org.junit.jupiter.api.extension.Extension;

      public class MyExtension implements Extension {}
      """);
    myFixture.addClass("""
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      import org.junit.jupiter.api.extension.ExtendWith;

      @Target(ElementType.TYPE)
      @Retention(RetentionPolicy.RUNTIME)
      @ExtendWith(MyExtension.class)
      public @interface MyAppTest {}
      """);
    myFixture.addClass("""
      import org.junit.jupiter.api.Test;

      public class MyTest extends AbstractBaseTest {
        @Test
        publc void test() {}
      }
      """);
    PsiFile file = myFixture.configureByText("AbstractBaseTest.java", """
      @MyAppTest
      public abstract class AbstractBaseTest {
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
      assertTrue(JUnitUtil.isJUnit5TestClass(aClass, false),
                 "Abstract class with @ExtendWith supplied via a composed (meta) annotation " +
                 "should be recognized as a JUnit 5 test class");
    });
  }
}
