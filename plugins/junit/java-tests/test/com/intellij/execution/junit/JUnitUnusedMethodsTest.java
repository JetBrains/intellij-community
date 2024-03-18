// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class JUnitUnusedMethodsTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit; public @interface Test {}");
    myFixture.addClass("package org.junit.runners; public class Parameterized { public @interface Parameters {} public @interface Parameter {}}");
    myFixture.addClass("package org.junit.runner; public @interface RunWith {Class value();}");
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
    myFixture.enableInspections(new UnusedDeclarationInspection(true));
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
}
