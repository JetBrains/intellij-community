// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.inspections.internal.UnsafeReturnStatementVisitorInspection;

public class UnsafeReturnStatementVisitorTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnsafeReturnStatementVisitorInspection());
  }

  public void testInsertVisitLambda() {
    myFixture.addClass("package com.intellij.psi;\n" +
                       "public interface PsiLambdaExpression{}");
    myFixture.addClass("package com.intellij.psi;\n" +
                       "public abstract class JavaRecursiveElementVisitor{" +
                       "  public void visitLambdaExpression(PsiLambdaExpression expression) {}" +
                       "  public void visitClass(PsiClass aClass) {}" +
                       "}");
    myFixture.addClass("package com.intellij.psi;\n" +
                       "public interface PsiReturnStatement{}");
    myFixture.addClass("package com.intellij.psi;\n" +
                       "public interface PsiClass{}");

    myFixture.configureByText(JavaFileType.INSTANCE, """
      import com.intellij.psi.*;
      class Te<caret>st extends JavaRecursiveElementVisitor {
         public void visitReturnStatement(PsiReturnStatement statement) {}
         public void visitClass(PsiClass aClass) {}
      }""");
    IntentionAction intention = myFixture.findSingleIntention("Insert visitLambdaExpression method");
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                            import com.intellij.psi.*;
                            class Test extends JavaRecursiveElementVisitor {
                               public void visitReturnStatement(PsiReturnStatement statement) {}
                               public void visitClass(PsiClass aClass) {}

                                @Override
                                public void visitLambdaExpression(PsiLambdaExpression expression) {
                                }
                            }""");
  }
}
