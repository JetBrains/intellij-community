/*
 * Copyright 2009-2013 Bas Leijdekkers
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
package com.siyeh.ipp.forloop;

import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.VariableAccessUtils;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

class ReverseForLoopDirectionPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiJavaToken keyword = (PsiJavaToken)element;
    final IElementType tokenType = keyword.getTokenType();
    if (!JavaTokenType.FOR_KEYWORD.equals(tokenType)) {
      return false;
    }
    final PsiElement parent = keyword.getParent();
    if (!(parent instanceof PsiForStatement)) {
      return false;
    }
    final PsiForStatement forStatement = (PsiForStatement)parent;
    final PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement)) {
      return false;
    }
    final PsiDeclarationStatement declarationStatement =
      (PsiDeclarationStatement)initialization;
    final PsiElement[] declaredElements =
      declarationStatement.getDeclaredElements();
    if (declaredElements.length != 1) {
      return false;
    }
    final PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiLocalVariable)) {
      return false;
    }
    final PsiVariable variable = (PsiVariable)declaredElement;
    final PsiType type = variable.getType();
    if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) {
      return false;
    }
    final PsiExpression condition = forStatement.getCondition();
    if (!VariableAccessUtils.isVariableCompared(variable, condition)) {
      return false;
    }
    final PsiStatement update = forStatement.getUpdate();
    return VariableAccessUtils.isVariableIncrementOrDecremented(variable,
                                                                update);
  }
}