/*
 * Copyright 2003-2006 Bas Leijdekkers
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
package com.siyeh.ipp.parenthesis;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

public class AddClarifyingParenthesesPredicate implements PsiElementPredicate {

	public boolean satisfiedBy(PsiElement element) {
		if (!(element instanceof PsiBinaryExpression)) {
			return element instanceof PsiInstanceOfExpression &&
			       element.getParent()instanceof PsiBinaryExpression;
		}
		final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
		final PsiJavaToken sign = binaryExpression.getOperationSign();
		final IElementType tokenType = sign.getTokenType();
		final PsiElement parent = element.getParent();
		if (parent instanceof PsiBinaryExpression) {
			final PsiBinaryExpression parentBinaryExpression = (PsiBinaryExpression)parent;
			final PsiJavaToken parentSign = parentBinaryExpression.getOperationSign();
			final IElementType parentTokenType = parentSign.getTokenType();
			if (!tokenType.equals(parentTokenType)) {
				return true;
			}
		} else if (parent instanceof PsiInstanceOfExpression) {
			return true;
		}
		final PsiExpression lhs = binaryExpression.getLOperand();
		final PsiExpression rhs = binaryExpression.getROperand();
		if (lhs instanceof PsiBinaryExpression) {
			final PsiBinaryExpression lhsBinaryExpression = (PsiBinaryExpression)lhs;
			final PsiJavaToken lhsSign = lhsBinaryExpression.getOperationSign();
			final IElementType lhsTokenType = lhsSign.getTokenType();
			if (!tokenType.equals(lhsTokenType)) {
				return true;
			}
		} else if (lhs instanceof PsiInstanceOfExpression) {
			return true;
		}
		if (rhs instanceof PsiBinaryExpression) {
			final PsiBinaryExpression rhsBinaryExpression = (PsiBinaryExpression)rhs;
			final PsiJavaToken rhsSign = rhsBinaryExpression.getOperationSign();
			final IElementType rhsTokenType = rhsSign.getTokenType();
			if (!tokenType.equals(rhsTokenType)) {
				return true;
			}
		} else if (rhs instanceof PsiInstanceOfExpression) {
			return true;
		}
		return false;
	}
}