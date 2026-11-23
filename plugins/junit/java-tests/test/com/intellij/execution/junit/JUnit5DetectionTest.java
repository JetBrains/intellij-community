// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.testframework.TestIconProvider;
import com.intellij.junit.testFramework.JUnitProjectDescriptor;
import com.intellij.junit.testFramework.MavenTestLib;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testIntegration.TestFramework;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

  @Test
  public void testLibraryClassStartsNoInheritorsSearch(@TestDisposable Disposable disposable) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    AtomicInteger searches = countInheritorsSearches(disposable);
    ReadAction.runBlocking(() -> {
      PsiClass libraryClass = myFixture.findClass("org.junit.jupiter.api.function.Executable");
      PsiMethod abstractMethod = libraryClass.getMethods()[0];
      assertNull(new TestIconProvider().getIcon(abstractMethod, 0));
      assertFalse(TestFrameworks.getInstance().isTestClass(libraryClass));
    });
    assertEquals(0, searches.get());
  }

  @Test
  public void testMethodIconOfAbstractClassStartsNoInheritorsSearch(@TestDisposable Disposable disposable) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
      import org.junit.jupiter.api.Test;

      public class InheritedTest extends AbstractTestClass {
        @Override
        @Test
        void testFromBase() {}
      }
      """);
    PsiFile file = myFixture.configureByText("AbstractTestClass.java", """
      public abstract class AbstractTestClass {
        abstract void testFromBase();
      }
      """);
    AtomicInteger searches = countInheritorsSearches(disposable);
    ReadAction.runBlocking(() -> {
      PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
      PsiMethod abstractMethod = aClass.getMethods()[0];
      assertNull(new TestIconProvider().getIcon(abstractMethod, 0));
      assertEquals(0, searches.get());

      TestFramework framework = TestFrameworks.detectFramework(aClass);
      assertInstanceOf(JupiterFramework.class, framework);
      assertTrue(framework.isTestMethod(abstractMethod, false));
    });
  }

  private static AtomicInteger countInheritorsSearches(Disposable disposable) {
    AtomicInteger searches = new AtomicInteger();
    // A search stops at the first match, so only the first executor sees each search.
    ClassInheritorsSearch.EP_NAME.getPoint().registerExtension((_, _) -> {
      searches.incrementAndGet();
      return true;
    }, LoadingOrder.FIRST, disposable);
    return searches;
  }
}
