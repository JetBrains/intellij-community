// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.geb.GebTestsTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class NavigateDelegatedClsMethodsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GebTestsTest.DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "resolve/clsMethod");

  @Test
  public void testNavigationInGroovy() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.groovy", """
      import geb.Page;
      
      class A extends Page {
          void foo() {
              fin<caret>d(".div");
          }
      }
      """);
    ReadAction.runBlocking(() -> {
      TargetElementUtil instance = TargetElementUtil.getInstance();
      PsiElement resolved = instance.findTargetElement(myFixture.getEditor(), instance.getAllAccepted(), myFixture.getEditor().getCaretModel().getOffset());
      assertInstanceOf(PsiMethod.class, resolved);
      assertEquals("Page", ((PsiMethod)resolved).getContainingClass().getName());
    });
  }

  @Test
  public void testNavigationInJava() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", """
      import geb.Page;
      
      class A extends Page {
          void foo() {
              fin<caret>d(".div");
          }
      }
      """);
    ReadAction.runBlocking(() -> {
      TargetElementUtil instance = TargetElementUtil.getInstance();
      PsiElement resolved = instance.findTargetElement(myFixture.getEditor(), instance.getAllAccepted(), myFixture.getEditor().getCaretModel().getOffset())
        .getNavigationElement();
      assertInstanceOf(PsiMethod.class, resolved);
      assertEquals("Page", ((PsiMethod)resolved).getContainingClass().getName());
    });
  }
}
