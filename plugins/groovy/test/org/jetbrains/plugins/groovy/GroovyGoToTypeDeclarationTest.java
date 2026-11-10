// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class GroovyGoToTypeDeclarationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testGoToTypeDeclarationMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    ReadAction.runBlocking(() -> {
      PsiElement res = GotoTypeDeclarationAction.findSymbolType(myFixture.getEditor(), myFixture.getCaretOffset());
      assertInstanceOf(PsiClass.class, res);
      assertEquals("B", ((PsiClass)res).getName());
    });
  }

  @Test
  public void testGoToTypeDeclarationVariable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    ReadAction.runBlocking(() -> {
      PsiElement res = GotoTypeDeclarationAction.findSymbolType(myFixture.getEditor(), myFixture.getCaretOffset());
      assertInstanceOf(PsiClass.class, res);
      assertEquals("B", ((PsiClass)res).getName());
    });
  }
}
