/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;

class NestingDepthVisitor extends GroovyRecursiveElementVisitor {

  private int m_maximumDepth = 0;
  private int m_currentDepth = 0;

  @Override
  public void visitBlockStatement(GrBlockStatement statement) {
    final PsiElement parent = statement.getParent();
    final boolean isAlreadyCounted =
        parent instanceof GrWhileStatement ||
            parent instanceof GrForStatement ||
            parent instanceof GrIfStatement;
    if (!isAlreadyCounted) {
      enterScope();
    }
    super.visitBlockStatement(statement);
    if (!isAlreadyCounted) {
      exitScope();
    }
  }


  @Override
  public void visitForStatement(@NotNull GrForStatement statement) {
    enterScope();
    super.visitForStatement(statement);
    exitScope();
  }

  @Override
  public void visitIfStatement(@NotNull GrIfStatement statement) {
    boolean isAlreadyCounted = false;
    if (statement.getParent() instanceof GrIfStatement) {
      final GrIfStatement parent = (GrIfStatement) statement.getParent();
      assert parent != null;
      final GrStatement elseBranch = parent.getElseBranch();
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
  public void visitTryStatement(@NotNull GrTryCatchStatement statement) {
    enterScope();
    super.visitTryStatement(statement);
    exitScope();
  }

  @Override
  public void visitSwitchStatement(@NotNull GrSwitchStatement statement) {
    enterScope();
    super.visitSwitchStatement(statement);
    exitScope();
  }

  @Override
  public void visitWhileStatement(@NotNull GrWhileStatement statement) {
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
