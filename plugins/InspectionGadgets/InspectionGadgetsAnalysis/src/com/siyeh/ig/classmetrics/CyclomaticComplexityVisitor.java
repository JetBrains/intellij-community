/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classmetrics;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class CyclomaticComplexityVisitor extends JavaRecursiveElementWalkingVisitor {
  private int m_complexity = 1;

  @Override
  public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
    // to call to super, to keep this from drilling down
  }

  @Override
  public void visitForStatement(@NotNull PsiForStatement statement) {
    super.visitForStatement(statement);
    m_complexity++;
  }

  @Override
  public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
    super.visitForeachStatement(statement);
    m_complexity++;
  }

  @Override
  public void visitIfStatement(@NotNull PsiIfStatement statement) {
    super.visitIfStatement(statement);
    m_complexity++;
  }

  @Override
  public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
    super.visitDoWhileStatement(statement);
    m_complexity++;
  }

  @Override
  public void visitConditionalExpression(PsiConditionalExpression expression) {
    super.visitConditionalExpression(expression);
    m_complexity++;
  }

  @Override
  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    final PsiCodeBlock body = statement.getBody();
    if (body == null) {
      return;
    }
    final PsiStatement[] statements = body.getStatements();
    boolean pendingLabel = false;
    for (final PsiStatement child : statements) {
      if (child instanceof PsiSwitchLabelStatement) {
        if (!pendingLabel) {
          m_complexity++;
        }
        pendingLabel = true;
      }
      else {
        pendingLabel = false;
      }
    }
  }

  @Override
  public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
    super.visitWhileStatement(statement);
    m_complexity++;
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    super.visitPolyadicExpression(expression);
    final IElementType token = expression.getOperationTokenType();
    if (token.equals(JavaTokenType.ANDAND) || token.equals(JavaTokenType.OROR)) {
      m_complexity += expression.getOperands().length - 1;
    }
  }

  @Override
  public void visitCatchSection(PsiCatchSection section) {
    super.visitCatchSection(section);
    m_complexity++;
  }

  public int getComplexity() {
    return m_complexity;
  }

  public void reset() {
    m_complexity = 1;
  }
}
