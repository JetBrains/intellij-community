/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.concatenation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class JoinConcatenatedStringLiteralsIntention extends Intention {

	@NotNull
	protected PsiElementPredicate getElementPredicate() {
		return new StringConcatPredicate();
	}

	public void processIntention(PsiElement element)
			throws IncorrectOperationException {
		final PsiBinaryExpression binaryExpression =
				(PsiBinaryExpression)element.getParent();
		assert binaryExpression != null;
		final PsiBinaryExpression copy =
				(PsiBinaryExpression)binaryExpression.copy();
		final PsiExpression lhs = copy.getLOperand();
		String newExpression = "";
		if (lhs instanceof PsiBinaryExpression) {
			final PsiBinaryExpression lhsBinaryExpression =
					(PsiBinaryExpression)lhs;
			newExpression += getLeftSideText(lhsBinaryExpression);
			final PsiExpression rightSide = lhsBinaryExpression.getROperand();
			assert rightSide != null;
			lhs.replace(rightSide);
		}
		newExpression += '"' + computeConstantStringExpression(copy) + '"';
		replaceExpression(newExpression, binaryExpression);
	}

	/**
	 * handles the specified expression as if it was part of a string expression
	 * (even if it's of another type) and computes a constant string expression
	 * from it.
	 */
	private static String computeConstantStringExpression(
			PsiBinaryExpression expression) {
		final PsiManager manager = expression.getManager();
		final PsiConstantEvaluationHelper constantEvaluationHelper =
				manager.getConstantEvaluationHelper();
		final PsiExpression lhs = expression.getLOperand();
		final Object lhsConstant =
				constantEvaluationHelper.computeConstantExpression(lhs);
        final String lhsText;
        if (lhsConstant == null) {
            lhsText = "";
        } else {
            lhsText = lhsConstant.toString();
        }
        String result;
		if (lhsText.length() == 0) {
			result = "";
		} else {
			result = lhsText;
		}
		final PsiExpression rhs = expression.getROperand();
		final Object rhsConstant =
				constantEvaluationHelper.computeConstantExpression(rhs);
        if (rhsConstant != null) {
            result += rhsConstant.toString();
        }
		result = StringUtil.escapeStringCharacters(result);
		return result;
	}

	private static String getLeftSideText(
			PsiBinaryExpression binaryExpression) {
		final PsiExpression lhs = binaryExpression.getLOperand();
		final PsiJavaToken sign = binaryExpression.getOperationSign();
		return lhs.getText() + sign.getText();
	}
}
