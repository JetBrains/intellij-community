/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

class NegationCountVisitor extends JavaRecursiveElementVisitor {

  private final boolean myIgnoreInAssertStatements;
  private int m_count = 0;

  public NegationCountVisitor(boolean ignoreInAssertStatements) {
    myIgnoreInAssertStatements = ignoreInAssertStatements;
  }

  @Override
  public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
    super.visitBinaryExpression(expression);
    final IElementType tokenType = expression.getOperationTokenType();
    if (tokenType.equals(JavaTokenType.NE)) {
      m_count++;
    }
  }

  @Override
  public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
    // no call to super, to keep it from drilling into anonymous classes
  }

  @Override
  public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
    super.visitPrefixExpression(expression);
    if (expression.getOperationTokenType().equals(JavaTokenType.EXCL)) {
      m_count++;
    }
  }

  @Override
  public void visitAssertStatement(PsiAssertStatement statement) {
    final int count = m_count;
    super.visitAssertStatement(statement);
    if (myIgnoreInAssertStatements) {
      m_count = count;
    }
  }

  public int getCount() {
    return m_count;
  }
}
