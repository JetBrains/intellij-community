// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import org.jetbrains.idea.devkit.inspections.internal.UnsafeReturnStatementVisitorInspection;

public abstract class UnsafeReturnStatementVisitorInsertVisitMethodsTestBase extends LightDevKitInspectionFixTestBase {
  public static final String INSERT_LAMBDA_AND_CLASS_VISIT_METHODS = "Insert visitLambdaExpression/visitClass methods";
  public static final String INSERT_LAMBDA_VISIT_METHOD = "Insert visitLambdaExpression method";
  public static final String INSERT_CLASS_VISIT_METHOD = "Insert visitClass method";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("""
      package com.intellij.psi;
      public abstract class PsiElementVisitor {}
      """);
    myFixture.addClass("""
      package com.intellij.psi;
      public interface PsiElement {
        void accept(PsiElementVisitor visitor);
      }
      """);
    myFixture.addClass("""
      package com.intellij.psi;
      public interface PsiClass extends PsiElement {}
      """);
    myFixture.addClass("""
      package com.intellij.psi;
      public interface PsiReturnStatement extends PsiElement {}
      """);
    myFixture.addClass("""
      package com.intellij.psi;
      public interface PsiLambdaExpression extends PsiElement {}
      """);
    myFixture.addClass("""
      package com.intellij.psi;
      public abstract class JavaElementVisitor extends PsiElementVisitor {
        public void visitClass(PsiClass aClass) {}
        public void visitReturnStatement(PsiReturnStatement statement) {}
        public void visitLambdaExpression(PsiLambdaExpression expression) {}
      }
      """);
    myFixture.addClass("""
      package com.intellij.psi;
      public abstract class JavaRecursiveElementVisitor extends JavaElementVisitor {}
      """);
    myFixture.addClass("""
      package com.intellij.psi;
      public abstract class JavaRecursiveElementWalkingVisitor extends JavaElementVisitor {}
      """);
    myFixture.enableInspections(new UnsafeReturnStatementVisitorInspection());
  }
}
