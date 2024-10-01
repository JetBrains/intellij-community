// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.geb.GebTestsTest;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class NavigateDelegatedClsMethodsTest extends LightGroovyTestCase {
  public void testNavigationInGroovy() {
    myFixture.configureByText("A.groovy", """
      import geb.Page;
      
      class A extends Page {
          void foo() {
              fin<caret>d(".div");
          }
      }
      """);
    TargetElementUtil instance = TargetElementUtil.getInstance();
    PsiElement resolved = instance.findTargetElement(getEditor(), instance.getAllAccepted(), getEditor().getCaretModel().getOffset());
    UsefulTestCase.assertInstanceOf(resolved, PsiMethod.class);
    TestCase.assertEquals("Page", ((PsiMethod)resolved).getContainingClass().getName());
  }

  public void testNavigationInJava() {
    myFixture.configureByText("A.java", """
      import geb.Page;
      
      class A extends Page {
          void foo() {
              fin<caret>d(".div");
          }
      }
      """);
    TargetElementUtil instance = TargetElementUtil.getInstance();
    PsiElement resolved = instance.findTargetElement(getEditor(), instance.getAllAccepted(), getEditor().getCaretModel().getOffset())
      .getNavigationElement();
    UsefulTestCase.assertInstanceOf(resolved, PsiMethod.class);
    TestCase.assertEquals("Page", ((PsiMethod)resolved).getContainingClass().getName());
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private final String basePath = TestUtils.getTestDataPath() + "resolve/clsMethod";
  private final LightProjectDescriptor projectDescriptor = GebTestsTest.DESCRIPTOR;
}
