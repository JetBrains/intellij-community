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
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

class NestingDepthVisitor extends JavaRecursiveElementWalkingVisitor {
  private final int myLimit;
  private int m_maximumDepth;
  private int m_currentDepth;

  public NestingDepthVisitor(int limit) {
    myLimit = limit;
  }


  @Override
  public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
    // no call to super, to keep this from drilling down
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
      enterScope(statement);
    }
    super.visitBlockStatement(statement);
  }

  @Override
  public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
    enterScope(statement);
    super.visitDoWhileStatement(statement);
  }

  @Override
  public void visitForStatement(@NotNull PsiForStatement statement) {
    enterScope(statement);
    super.visitForStatement(statement);
  }

  @Override
  public void visitIfStatement(@NotNull PsiIfStatement statement) {
    boolean isAlreadyCounted = false;
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiIfStatement) {
      final PsiStatement elseBranch = ((PsiIfStatement)parent).getElseBranch();
      if (statement.equals(elseBranch)) {
        isAlreadyCounted = true;
      }
    }
    if (!isAlreadyCounted) {
      enterScope(statement);
    }
    super.visitIfStatement(statement);
  }

  @Override
  public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
    enterScope(statement);
    super.visitSynchronizedStatement(statement);
  }

  @Override
  public void visitTryStatement(@NotNull PsiTryStatement statement) {
    enterScope(statement);
    super.visitTryStatement(statement);
  }

  @Override
  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    enterScope(statement);
    super.visitSwitchStatement(statement);
  }

  @Override
  public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
    enterScope(statement);
    super.visitWhileStatement(statement);
  }

  @Override
  protected void elementFinished(@NotNull PsiElement element) {
    exitScope(element);
  }

  private final Set<PsiElement> scopeEntered = new THashSet<>();
  private void enterScope(PsiElement element) {
    scopeEntered.add(element);
    m_currentDepth++;
    if ((m_maximumDepth = Math.max(m_maximumDepth, m_currentDepth)) > myLimit) {
      stopWalking();
    }
  }

  private void exitScope(PsiElement element) {
    if (scopeEntered.remove(element)) {
      m_currentDepth--;
    }
  }

  int getMaximumDepth() {
    return m_maximumDepth;
  }
}
