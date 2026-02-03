// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.junit.testFramework.JUnitLibrary;
import com.intellij.junit.testFramework.JUnitProjectDescriptor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class JUnitUnusedMethodsTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnusedDeclarationInspection(true));
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return new JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUnitLibrary.JUNIT4, JUnitLibrary.JUNIT5);
  }


  public void testRecognizeNestedAbstractClass() {
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

  public void testRecognizeTestMethodInParameterizedClass() {
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

  public void testRecognizeEnumSource() {
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

  public void testBeforeSuiteIsImplicitUsage() {
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
