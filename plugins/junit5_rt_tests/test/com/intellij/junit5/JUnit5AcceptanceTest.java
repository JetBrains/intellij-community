// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.junit.JUnit5Framework;
import com.intellij.psi.PsiClass;
import com.intellij.testIntegration.TestFramework;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class JUnit5AcceptanceTest extends JUnitAcceptanceCodeInsightTestBase {
  @Override
  protected Class<? extends TestFramework> framework() {
    return JUnit5Framework.class;
  }

  @Override
  protected String runParameter() {
    return "-junit5";
  }

  @Override
  protected String addToClasspathFixText() {
    return "Add 'JUnit5' to classpath";
  }

  @Test
  void rejectAbstractMethods() {
    PsiClass aClass = myFixture.addClass("""
                          import org.junit.jupiter.api.*;
                          /** @noinspection ALL*/
                          abstract class MyTest {
                            @Test abstract void method();
                          }
                          """);
    assertFalse(TestFrameworks.getInstance().isTestClass(aClass));
    assertFalse(TestFrameworks.getInstance().isTestMethod(aClass.getMethods()[0]));

    PsiClass iClass = myFixture.addClass("""
                          import org.junit.jupiter.api.*;
                          /** @noinspection ALL*/
                          abstract class InheritedTest extends MyTest {
                            @Override void method();
                          }
                          """);
    assertFalse(TestFrameworks.getInstance().isTestClass(iClass));
    assertFalse(TestFrameworks.getInstance().isTestMethod(iClass.getMethods()[0]));
  }
}
