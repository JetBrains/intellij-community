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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddClarifyingParenthesesIntention extends Intention {

	@NotNull protected
	PsiElementPredicate getElementPredicate() {
		return new AddClarifyingParenthesesPredicate();
	}

	protected void processIntention(@NotNull PsiElement element)
			throws IncorrectOperationException {
		final PsiExpression expression = getTopLevelExpression(element);
		if (expression == null) {
			return;
		}
		final String newExpression = createReplacementText(expression);
		replaceExpression(newExpression, expression);
	}

	@Nullable
	private static PsiExpression getTopLevelExpression(PsiElement element) {
		if (!(element instanceof PsiExpression)) {
			return null;
		}
		PsiExpression result = (PsiExpression)element;
		PsiElement parent = result.getParent();
		while (parent instanceof PsiBinaryExpression ||
				parent instanceof PsiParenthesizedExpression) {
			result = (PsiExpression)parent;
			parent = result.getParent();
		}
		return result;
	}

	private static String createReplacementText(PsiExpression element) {
		if (element instanceof PsiBinaryExpression) {
			final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
			final PsiExpression lhs = binaryExpression.getLOperand();
			final PsiJavaToken operationSign = binaryExpression.getOperationSign();
			final PsiExpression rhs = binaryExpression.getROperand();
			final PsiElement parent = element.getParent();
			final String signText = operationSign.getText();
			if (parent instanceof PsiBinaryExpression) {
				final PsiBinaryExpression parentBinaryExpression = (PsiBinaryExpression)parent;
				final PsiJavaToken parentOperationSign = parentBinaryExpression.getOperationSign();
				if (signText.equals(parentOperationSign.getText())) {
					return createReplacementText(lhs) + signText + createReplacementText(rhs);
				}
				return '(' + createReplacementText(lhs) + signText +
				       createReplacementText(rhs) + ')';
			} else {
				return createReplacementText(lhs) + signText +
				       createReplacementText(rhs);
			}
		} else if (element instanceof PsiParenthesizedExpression) {
			final PsiParenthesizedExpression parenthesizedExpression =
					(PsiParenthesizedExpression)element;
			final PsiExpression expression = parenthesizedExpression.getExpression();
			return '(' + createReplacementText(expression) + ')';
		} else if (element instanceof PsiInstanceOfExpression) {
			return '(' + element.getText() + ')';
		}
		return element.getText();
	}
}