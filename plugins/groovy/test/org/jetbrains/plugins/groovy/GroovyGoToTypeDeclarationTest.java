// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class GroovyGoToTypeDeclarationTest extends LightJavaCodeInsightFixtureTestCase {
  public void testGoToTypeDeclarationMethod() {
    myFixture.configureByText("g.groovy", """
      class A {
      
        public def method() {
          return new B();
        }
      
        {
          method<caret>()
        }
      }
      
      class B {
      
      }
      """);

    PsiElement res = GotoTypeDeclarationAction.findSymbolType(myFixture.getEditor(), myFixture.getCaretOffset());
    assertInstanceOf(res, PsiClass.class);
    assertEquals("B", ((PsiClass)res).getName());
  }

  public void testGoToTypeDeclarationVariable() {
    myFixture.configureByText("g.groovy", """
      class A {
      
        {
          def a = new B()
          println(a<caret>)
        }
      }
      
      class B {
      
      }
      """);

    PsiElement res = GotoTypeDeclarationAction.findSymbolType(myFixture.getEditor(), myFixture.getCaretOffset());
    assertInstanceOf(res, PsiClass.class);
    assertEquals("B", ((PsiClass)res).getName());
  }
}
