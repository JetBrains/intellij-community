/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.bool;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.ipp.base.PsiElementPredicate;

import java.util.Arrays;

class ConjunctionPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
    final IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.ANDAND) && !tokenType.equals(JavaTokenType.OROR)) {
      return false;
    }
    return !PsiUtilCore.hasErrorElementChild(expression) &&
           Arrays.stream(expression.getOperands()).noneMatch(PsiUtilCore::hasErrorElementChild);
  }
}
