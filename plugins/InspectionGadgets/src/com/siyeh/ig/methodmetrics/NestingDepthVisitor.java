/*
 * Copyright 2003-2005 Dave Griffith
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

class NestingDepthVisitor extends JavaRecursiveElementVisitor {
  private int m_maximumDepth = 0;
  private int m_currentDepth = 0;


  @Override
  public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
    // to call to super, to keep this from drilling down
  }

  @Override
  public void visitBlockStatement(PsiBlockStatement statement) {
    final PsiElement parent = statement.getParent();
    final boolean isAlreadyCounted = parent instanceof PsiDoWhileStatement ||
                                     parent instanceof PsiWhileStatement ||
                                     parent instanceof PsiForStatement ||
                                     parent instanceof PsiIfStatement ||
                                     parent instanceof PsiSynchronizedStatement;
    if (!isAlreadyCounted) {
      enterScope();
    }
    super.visitBlockStatement(statement);
    if (!isAlreadyCounted) {
      exitScope();
    }
  }

  @Override
  public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
    enterScope();
    super.visitDoWhileStatement(statement);
    exitScope();
  }

  @Override
  public void visitForStatement(@NotNull PsiForStatement statement) {
    enterScope();
    super.visitForStatement(statement);
    exitScope();
  }

  @Override
  public void visitIfStatement(@NotNull PsiIfStatement statement) {
    boolean isAlreadyCounted = false;
    if (statement.getParent() instanceof PsiIfStatement) {
      final PsiIfStatement parent = (PsiIfStatement)statement.getParent();
      assert parent != null;
      final PsiStatement elseBranch = parent.getElseBranch();
      if (statement.equals(elseBranch)) {
        isAlreadyCounted = true;
      }
    }
    if (!isAlreadyCounted) {
      enterScope();
    }
    super.visitIfStatement(statement);
    if (!isAlreadyCounted) {
      exitScope();
    }
  }

  @Override
  public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
    enterScope();
    super.visitSynchronizedStatement(statement);
    exitScope();
  }

  @Override
  public void visitTryStatement(@NotNull PsiTryStatement statement) {
    enterScope();
    super.visitTryStatement(statement);
    exitScope();
  }

  @Override
  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    enterScope();
    super.visitSwitchStatement(statement);
    exitScope();
  }

  @Override
  public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
    enterScope();
    super.visitWhileStatement(statement);
    exitScope();
  }

  private void enterScope() {
    m_currentDepth++;
    m_maximumDepth = Math.max(m_maximumDepth, m_currentDepth);
  }

  private void exitScope() {
    m_currentDepth--;
  }

  public int getMaximumDepth() {
    return m_maximumDepth;
  }
}
