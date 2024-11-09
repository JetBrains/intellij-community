// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.TestIconProvider;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testIntegration.TestFramework;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class JUnitDetectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit; public @interface Test {}");
    myFixture.addClass("package org.junit.runners; public class Parameterized { public @interface Parameters {} public @interface Parameter {}}");
    myFixture.addClass("package org.junit.runner; public @interface RunWith {Class<?> value();}");
  }

  public void testInnerClassesForKnownRunners() {
    PsiFile file = myFixture.configureByText("A.java", """
      import org.junit.Test;
      import org.junit.runner.RunWith;
      import org.junit.runners.Parameterized;
      import java.util.*;
      @RunWith(Parameterized.class)
      public class A {
        @Parameterized.Parameters
        public static Collection<Object[]> data() {
          return Arrays.asList(new Object[] {"11"}, new Object[] {"12"});
        }
        @Parameterized.Parameter
        public String myJUnitVersion;
        @Test
        public void ignoredTestMethod() throws Exception {}
        public class Foo {
          public void testMe() {}
         }
      }
      """);
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertNotNull(framework);
    assertTrue(framework.isTestClass(aClass));
    PsiClass innerClass = aClass.getInnerClasses()[0];
    assertFalse(framework.isTestClass(innerClass));
    assertFalse(framework.isTestMethod(innerClass.getMethods()[0]));
  }
  
  public void testJUnit3TestCase() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    PsiFile file = myFixture.configureByText("Foo.java", """
      public class Foo extends junit.framework.TestCase {
          public class Bar extends Foo {
              public void testFoo() {
              }
          }
          public void testFoo2() {
          }
      }""");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertNotNull(framework);
    assertTrue(framework.isTestClass(aClass));
    PsiClass innerClass = aClass.getInnerClasses()[0];
    assertFalse(framework.isTestClass(innerClass));
  }

  public void testJUnit3TestCaseInDumbMode() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    PsiFile file = myFixture.configureByText("Foo.java", """
      public class Foo extends junit.framework.TestCase {
          public class Bar extends Foo {
              public void testFoo() {
              }
          }
          public void testFoo2() {
          }
      }""");
    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
      TestFramework framework = TestFrameworks.detectFramework(aClass);
      assertNotNull(framework);
      assertTrue(framework.isTestClass(aClass));
      PsiClass innerClass = aClass.getInnerClasses()[0];
      assertFalse(framework.isTestClass(innerClass));
    });
  }

  public void testKnownClassInheritor() {
    myFixture.addClass("package p; import org.junit.*; import org.junit.runners.*; public class Theories extends Parameterized {}");
    myFixture.addClass("package p; public @interface Theory {}"); //custom annotation
    PsiFile file = myFixture.configureByText("A.java", """
      import org.junit.Test;
      import org.junit.runner.RunWith;
      import p.Theories;
      import java.util.*;
      @RunWith(Theories.class)
      public class A {
        @Theory
        public void validTestMethod(int param) {}
        public class Foo {
          public void testMe() {}
         }
      }
      """);
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertNotNull(framework);
    assertTrue(framework.isTestClass(aClass));
    assertTrue(framework.isTestMethod(aClass.getMethods()[0]));
    PsiClass innerClass = aClass.getInnerClasses()[0];
    assertFalse(framework.isTestClass(innerClass));
    assertFalse(framework.isTestMethod(innerClass.getMethods()[0]));
  }

  public void testCustomRunner() {
    myFixture.addClass("import org.junit.runner.Runner;" +
                       "public class CustomRunner extends Runner {}");
    PsiFile file = myFixture.configureByText("CustomRunnerTest.java",
                                             """
                                               import org.junit.rules.ExternalResource;
                                               import org.junit.runner.RunWith;
                                               @RunWith(CustomRunner.class)
                                               public class CustomRunnerTest {
                                               @org.junit.Rule
                                               public ExternalResource noTest() {return null;}
                                               public void foo() {}}
                                               """);

    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertNotNull(framework);
    assertTrue(framework.isTestClass(aClass));
    assertFalse(framework.isTestMethod(aClass.getMethods()[0]));
    assertTrue(framework.isTestMethod(aClass.getMethods()[1]));
  }

  public void testMockitoRunner() {
    myFixture.addClass("package org.mockito.junit; import org.junit.runner.Runner; import org.junit.runner.manipulation.Filterable; " +
                                            "public class MockitoJUnitRunner extends Runner implements Filterable {}");
    PsiFile file = myFixture.configureByText("MockitoRunnerTest.java",
                                             """
                                               import org.junit.rules.ExternalResource;
                                               import org.mockito.junit.MockitoJUnitRunner;
                                               import org.junit.runner.RunWith;
                                               @RunWith(MockitoJUnitRunner.class)
                                               public class MockitoRunnerTest {
                                                 @org.junit.Rule
                                                 public ExternalResource noTest() {return null;}
                                                 public void foo() {}}
                                               """);

    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertNotNull(framework);
    assertTrue(framework.isTestClass(aClass));
    assertFalse(framework.isTestMethod(aClass.getMethods()[0]));
    assertFalse(framework.isTestMethod(aClass.getMethods()[1]));
  }

  public void testMockitoRunnerImDumbMode() {
    myFixture.addClass("package org.mockito.junit; import org.junit.runner.Runner; import org.junit.runner.manipulation.Filterable; " +
                                            "public class MockitoJUnitRunner extends Runner implements Filterable {}");
    PsiFile file = myFixture.configureByText("MockitoRunnerTest.java",
                                             """
                                               import org.junit.rules.ExternalResource;
                                               import org.mockito.junit.MockitoJUnitRunner;
                                               import org.junit.runner.RunWith;
                                               @RunWith(MockitoJUnitRunner.class)
                                               public class MockitoRunnerTest {
                                                 @org.junit.Rule
                                                 public ExternalResource noTest() {return null;}
                                                 public void foo() {}}
                                               """);
    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
      TestFramework framework = TestFrameworks.detectFramework(aClass);
      assertNotNull(framework);
      assertTrue(framework.isTestClass(aClass));
      assertFalse(framework.isTestMethod(aClass.getMethods()[0]));
      assertFalse(framework.isTestMethod(aClass.getMethods()[1]));
    });
  }

  public void testJqwikProperty() {
    myFixture.addClass("package net.jqwik.api;" +
                       "@org.junit.platform.commons.annotation.Testable public @interface Property {}");
    myFixture.addClass("package org.junit.platform.commons.annotation;" +
                       "public @interface Testable {}");
    myFixture.addClass("package net.jqwik.api; public @interface ForAll {}");
    PsiFile file = myFixture.configureByText("JqwikPropertyTest.java",
                                             """
                                               import net.jqwik.api.ForAll;
                                               import net.jqwik.api.Property;
                                               class JqwikPropertyTests {
                                               \t@Property
                                               \tvoid aProperty(@ForAll int anInt) {}
                                               }"""
    );

    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    assertTrue(JUnitUtil.isTestClass(aClass));
    PsiMethod psiMethod = aClass.getMethods()[0];
    assertTrue(JUnitUtil.isTestMethod(PsiLocation.fromPsiElement(psiMethod), false));
  }

  public void testableMethodTest() {
        myFixture.addClass("package org.junit.jupiter.api;" +
                       "@org.junit.platform.commons.annotation.Testable public @interface Test {}");
        myFixture.addClass("package org.junit.jupiter.api;" +
                       "@org.junit.platform.commons.annotation.Testable public @interface Disabled {}");
        PsiFile file = myFixture.configureByText("TestableClassTest.java",
                                                 """
                                                   class TestableClassTest {
                                                   \n\t@org.junit.jupiter.api.Test
                                                   \n\tvoid foo() {}
                                                   \n\tvoid foo1() {}
                                                   \n\t@org.junit.jupiter.api.Test
                                                   \n\t@org.junit.jupiter.api.Disabled
                                                   \n\tvoid foo2() {}
                                                   }""");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    assertTrue(JUnitUtil.isTestClass(aClass));
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    PsiMethod fooMethod = aClass.getMethods()[0];
    PsiMethod foo1Method = aClass.getMethods()[1];
    PsiMethod foo2Method = aClass.getMethods()[2];
    assertTrue(JUnitUtil.isTestMethod(PsiLocation.fromPsiElement(fooMethod)));
    assertFalse(JUnitUtil.isTestMethod(PsiLocation.fromPsiElement(foo1Method)));
    assertTrue(JUnitUtil.isTestMethod(PsiLocation.fromPsiElement(foo2Method)));

    assertFalse(framework.isIgnoredMethod(fooMethod));
    assertFalse(framework.isIgnoredMethod(foo1Method));
    assertTrue(framework.isIgnoredMethod(foo2Method));

    TestIconProvider provider = new TestIconProvider();

    assertIcons(
      provider.getIcon(fooMethod, Iconable.ICON_FLAG_VISIBILITY),
      "Method",
      "runConfigurations/testMark.svg",
      "nodes/c_public.svg"
    );
    assertIcons(
      provider.getIcon(fooMethod, 0),
      "Method",
      "runConfigurations/testMark.svg"
    );

    Icon foo1MethodIcon = provider.getIcon(foo1Method, 0);
    assertNull(foo1MethodIcon);

    assertIcons(
      provider.getIcon(foo2Method, Iconable.ICON_FLAG_VISIBILITY),
      "runConfigurations/ignoredTest.svg",
      "nodes/c_public.svg"
    );
    assertEquals("runConfigurations/ignoredTest.svg", provider.getIcon(foo2Method, 0).toString());
  }

  private void assertIcons(@Nullable Icon icon, @NotNull String @NotNull... iconStrings) {
    assertNotNull(icon);
    assertTrue(icon instanceof LayeredIcon);
    LayeredIcon layeredIcon = (LayeredIcon)icon;
    assertEquals(iconStrings.length, layeredIcon.getIconCount());
    for (int i = 0; i < iconStrings.length; i++) {
      Icon layer = layeredIcon.getIcon(i);
      String iconString = iconStrings[i];
      assertNotNull(layer);
      assertEquals(iconString, layer.toString());
    }
  }

  public void testableClassTest() {
     myFixture.addClass("package org.junit.jupiter.api;" +
                       "@org.junit.platform.commons.annotation.Testable public @interface Test {}");
       
    PsiFile file = myFixture.configureByText("TestableClassTest.java",
                                             """
                                               @org.junit.platform.commons.annotation.Testable class TestableClassTest {
                                               \tvoid foo() {}
                                               }""");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    assertTrue(JUnitUtil.isTestClass(aClass));
    assertTrue(JUnitUtil.isTestMethod(PsiLocation.fromPsiElement(aClass.getMethods()[0])));
  }

  public void testMixJUnit3AndTestNG() {
    myFixture.addClass("package junit.framework; public class TestCase {}");

    myFixture.addClass("package org.testng.annotations;\n" +
                       "public @interface Test {}");
    myFixture.addClass("package org.testng.annotations;\n" +
                       "public @interface BeforeMethod {}");
    myFixture.addClass("package org.testng.annotations;\n" +
                       "public @interface AfterMethod {}");

    PsiFile file = myFixture.configureByText(
      "MyTest.java",
      """
        public class MyTest extends junit.framework.TestCase {\t
        @org.testng.annotations.Test public void fooBar(){}
        }""");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    List<TestFramework> frameworks = new ArrayList<>(TestFrameworks.detectApplicableFrameworks(aClass));
    Collections.sort(frameworks, Comparator.comparing(TestFramework::getName));
    assertEquals(2, frameworks.size());
    assertEquals("JUnit3", frameworks.get(0).getName());
    assertEquals("TestNG", frameworks.get(1).getName());
    assertTrue(JUnitUtil.isTestClass(aClass));
    assertFalse(JUnitUtil.isTestMethod(PsiLocation.fromPsiElement(aClass.getMethods()[0])));
  }
}
