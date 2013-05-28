/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class ReturnPointCountVisitor extends JavaRecursiveElementVisitor {

  private int m_count = 0;
  private final boolean ignoreGuardClauses;
  private boolean previousWasGuardClause = true;

  public ReturnPointCountVisitor(boolean ignoreGuardClauses) {
    this.ignoreGuardClauses = ignoreGuardClauses;
  }

  @Override
  public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
    // no call to super, to keep it from drilling into anonymous classes
  }

  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    super.visitReturnStatement(statement);
    if (ignoreGuardClauses && previousWasGuardClause) {
      return;
    }
    m_count++;
  }

  @Override
  public void visitStatement(PsiStatement statement) {
    super.visitStatement(statement);
    if (!previousWasGuardClause) {
      return;
    }
    if (statement instanceof PsiDeclarationStatement) {
      return;
    }
    final PsiElement parent = statement.getParent();
    if (!(parent instanceof PsiCodeBlock)) {
      return;
    }
    final PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiMethod)) {
      return;
    }
    previousWasGuardClause = isGuardClause(statement);
  }

  private static boolean isGuardClause(PsiStatement statement) {
    if (!(statement instanceof PsiIfStatement)) {
      return false;
    }
    final PsiIfStatement ifStatement = (PsiIfStatement)statement;
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch instanceof PsiReturnStatement) {
      return true;
    }
    if (!(thenBranch instanceof PsiBlockStatement)) {
      return false;
    }
    final PsiBlockStatement blockStatement = (PsiBlockStatement)thenBranch;
    final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
    final PsiStatement[] statements = codeBlock.getStatements();
    if (statements.length != 1) {
      return false;
    }
    final PsiStatement containedStatement = statements[0];
    return containedStatement instanceof PsiReturnStatement;
  }

  public int getCount() {
    return m_count;
  }
}