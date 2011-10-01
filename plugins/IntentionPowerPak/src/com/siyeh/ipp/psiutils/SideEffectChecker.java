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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

public class SideEffectChecker {

  private SideEffectChecker() {
    super();
  }

  public static boolean mayHaveSideEffects(PsiExpression exp) {
    final SideEffectsVisitor visitor = new SideEffectsVisitor();
    exp.accept(visitor);
    return visitor.mayHaveSideEffects();
  }

  private static class SideEffectsVisitor extends JavaRecursiveElementWalkingVisitor {

    private boolean mayHaveSideEffects = false;

    @Override
    public void visitElement(PsiElement element) {
      if (!mayHaveSideEffects) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      mayHaveSideEffects = true;
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      mayHaveSideEffects = true;
    }

    @Override
    public void visitAssignmentExpression(
      PsiAssignmentExpression expression) {
      mayHaveSideEffects = true;
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();

      if (tokenType.equals(JavaTokenType.PLUSPLUS) ||
          tokenType.equals(JavaTokenType.MINUSMINUS)) {
        mayHaveSideEffects = true;
      }
    }

    @Override
    public void visitPostfixExpression(PsiPostfixExpression expression) {
      super.visitPostfixExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();

      if (tokenType.equals(JavaTokenType.PLUSPLUS) ||
          tokenType.equals(JavaTokenType.MINUSMINUS)) {
        mayHaveSideEffects = true;
      }
    }

    public boolean mayHaveSideEffects() {
      return mayHaveSideEffects;
    }
  }
}