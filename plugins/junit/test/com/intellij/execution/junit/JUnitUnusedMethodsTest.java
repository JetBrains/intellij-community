/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
