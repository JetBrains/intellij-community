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
package com.siyeh.ipp.equality;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ipp.base.PsiElementPredicate;

class ObjectEqualityPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression expression = (PsiBinaryExpression)element;
    final IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.NE) &&
        !tokenType.equals(JavaTokenType.EQEQ)) {
      return false;
    }
    final PsiExpression lhs = expression.getLOperand();
    final PsiType lhsType = lhs.getType();
    if (lhsType == null || lhsType instanceof PsiPrimitiveType || TypeConversionUtil.isEnumType(lhsType)) {
      return false;
    }
    final PsiExpression rhs = expression.getROperand();
    if (rhs == null) {
      return false;
    }
    final PsiType rhsType = rhs.getType();
    return !(rhsType == null || rhsType instanceof PsiPrimitiveType || TypeConversionUtil.isEnumType(rhsType));
  }
}