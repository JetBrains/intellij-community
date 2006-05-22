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
package com.siyeh.ipp.parenthesis;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class RemoveUnnecessaryParenthesesIntention extends Intention {

	@NotNull
	public PsiElementPredicate getElementPredicate() {
		return new UnnecessaryParenthesesPredicate();
	}

	public void processIntention(PsiElement element)
			throws IncorrectOperationException {
		PsiExpression expression = (PsiExpression)element;
		while (expression.getParent() instanceof PsiExpression) {
			expression = (PsiExpression)expression.getParent();
			assert expression != null;
		}
        final String newExpression =
				ParenthesesUtils.removeParentheses(expression);
		replaceExpression(newExpression, expression);
	}
}