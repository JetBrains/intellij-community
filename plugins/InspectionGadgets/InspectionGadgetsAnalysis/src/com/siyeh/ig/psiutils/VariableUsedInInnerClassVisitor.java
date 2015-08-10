/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class VariableUsedInInnerClassVisitor extends JavaRecursiveElementWalkingVisitor {
  @NotNull private final PsiVariable variable;
  private boolean usedInInnerClass;
  private int inInnerClassCount;

  VariableUsedInInnerClassVisitor(@NotNull PsiVariable variable) {
    this.variable = variable;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (!usedInInnerClass) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitJavaToken(PsiJavaToken token) {
    super.visitJavaToken(token);
    PsiElement parent = token.getParent();
    if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      // have to be that complex because anonymous class argument list should not be treated as insideInner
      if (token.getTokenType() == JavaTokenType.LBRACE && aClass.getLBrace() == token) {
        inInnerClassCount++;
      }
      if (token.getTokenType() == JavaTokenType.RBRACE && aClass.getRBrace() == token) {
        inInnerClassCount--;
      }
    }
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
    if (usedInInnerClass) {
      return;
    }
    super.visitReferenceExpression(referenceExpression);
    if (inInnerClassCount > 0) {
      final PsiElement target = referenceExpression.resolve();
      if (variable.equals(target)) {
        usedInInnerClass = true;
      }
    }
  }

  boolean isUsedInInnerClass() {
    return usedInInnerClass;
  }
}