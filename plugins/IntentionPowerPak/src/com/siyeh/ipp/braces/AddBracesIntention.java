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
package com.siyeh.ipp.braces;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AddBracesIntention extends MutablyNamedIntention {

	@NotNull
	protected PsiElementPredicate getElementPredicate() {
		return new AddBracesPredicate();
	}

	protected String getTextForElement(PsiElement element) {
		final PsiElement parent = element.getParent();
		@NonNls final String keyword;
		if (parent instanceof PsiIfStatement) {
			final PsiIfStatement ifStatement = (PsiIfStatement)parent;
			final PsiStatement elseBranch = ifStatement.getElseBranch();
			if (element.equals(elseBranch)) {
				keyword = PsiKeyword.ELSE;
			} else {
				keyword = PsiKeyword.IF;
			}
		} else {
			final PsiElement firstChild = parent.getFirstChild();
			assert firstChild != null;
			keyword = firstChild.getText();
		}
		return IntentionPowerPackBundle.message("add.braces.intention.name", keyword);
	}

	protected void processIntention(@NotNull PsiElement element)
			throws IncorrectOperationException {
		final PsiStatement statement = (PsiStatement)element;
		final String newStatement = "{\n" + element.getText() + "\n}";
		replaceStatement(newStatement, statement);
	}
}