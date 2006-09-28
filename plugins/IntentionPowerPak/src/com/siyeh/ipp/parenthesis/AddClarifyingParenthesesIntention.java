/*
 * Copyright 2006 Bas Leijdekkers
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
		final StringBuilder newExpression =
                createReplacementText(expression, new StringBuilder());
		replaceExpression(newExpression.toString(), expression);
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

	private static StringBuilder createReplacementText(PsiExpression element,
                                                       StringBuilder out) {
		if (element instanceof PsiBinaryExpression) {
			final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)element;
			final PsiExpression lhs = binaryExpression.getLOperand();
			final PsiJavaToken operationSign =
                    binaryExpression.getOperationSign();
			final PsiExpression rhs = binaryExpression.getROperand();
			final PsiElement parent = element.getParent();
			final String signText = operationSign.getText();
            final PsiElement lhsNextSibling = lhs.getNextSibling();
            final PsiElement rhsPrevSibling;
            if (rhs != null) {
                rhsPrevSibling = rhs.getPrevSibling();
            } else {
                rhsPrevSibling = null;
            }
            if (parent instanceof PsiBinaryExpression) {
				final PsiBinaryExpression parentBinaryExpression =
                        (PsiBinaryExpression)parent;
				final PsiJavaToken parentOperationSign =
                        parentBinaryExpression.getOperationSign();
                if (!signText.equals(parentOperationSign.getText())) {
                    out.append("(");
                    createReplacementText(lhs, out);
                    if (lhsNextSibling instanceof PsiWhiteSpace) {
                        out.append(lhsNextSibling.getText());
                    }
                    out.append(signText);
                    if (rhsPrevSibling instanceof PsiWhiteSpace) {
                        out.append(rhsPrevSibling.getText());
                    }
                    createReplacementText(rhs, out);
                    out.append(')');
                    return out;
                }
            }
            createReplacementText(lhs, out);
            if (lhsNextSibling instanceof PsiWhiteSpace) {
                out.append(lhsNextSibling.getText());
            }
            out.append(signText);
            if (rhsPrevSibling instanceof PsiWhiteSpace) {
                out.append(rhsPrevSibling.getText());
            }
            createReplacementText(rhs, out);
        } else if (element instanceof PsiParenthesizedExpression) {
			final PsiParenthesizedExpression parenthesizedExpression =
					(PsiParenthesizedExpression)element;
			final PsiExpression expression =
                    parenthesizedExpression.getExpression();
            out.append('(');
            createReplacementText(expression, out);
            out.append(')');
		} else if (element instanceof PsiInstanceOfExpression) {
            out.append('(');
            out.append(element.getText());
            out.append(')');
		} else if (element != null) {
            out.append(element.getText());
        }
        return out;
    }
}