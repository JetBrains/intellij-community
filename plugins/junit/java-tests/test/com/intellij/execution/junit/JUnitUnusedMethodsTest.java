// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.junit.testFramework.JUnitProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.junit.testFramework.MavenTestLib.JUNIT4;
import static com.intellij.junit.testFramework.MavenTestLib.JUNIT5;
import static com.intellij.pom.java.LanguageLevel.HIGHEST;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class JUnitUnusedMethodsTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(new JUnitProjectDescriptor(HIGHEST, JUNIT4, JUNIT5));
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().enableInspections(new UnusedDeclarationInspection(true));
  }

  @Test
  public void testRecognizeNestedAbstractClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("ExampleTest.java", """
      import org.junit.jupiter.api.Nested;
      import org.junit.jupiter.api.Test;
      
      public class ExampleTest {
          abstract class BaseTest {
              @Test
              public void runTest() {}
          }
      
          @Nested
          class ChildTest extends BaseTest {}
      }
      """);
    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testRecognizeTestMethodInParameterizedClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", """
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
      }
      """);
    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testRecognizeEnumSource() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """           
      enum Foo { AAA, BBB, <warning descr="Field 'CCC' is never used">CCC</warning> }
      
      public class Test {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(value = Foo.class, names = {"AAA", "BBB"})
        void valid() {}
      }
      """);
    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testBeforeSuiteIsImplicitUsage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MySuite.java", """
      import org.junit.platform.suite.api.*;
      
      @Suite
      public class MySuite {
        @BeforeSuite
        public static void beforeSuite() {}
      
        @AfterSuite
        public static void afterSuite() {}
      }
      """);
    myFixture.testHighlighting(true, false, false);
  }
}
