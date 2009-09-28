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
package com.siyeh.ipp.braces;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

class AddBracesPredicate implements PsiElementPredicate
{
	public boolean satisfiedBy(@NotNull PsiElement element)
	{
		if (!(element instanceof PsiStatement))
		{
			return false;
		}
		if (element instanceof PsiBlockStatement)
		{
			return false;
		}
		final PsiElement parent = element.getParent();
		if (parent instanceof PsiIfStatement)
		{
			final PsiIfStatement ifStatement = (PsiIfStatement)parent;
			final PsiStatement elseBranch = ifStatement.getElseBranch();
			return !(element.equals(elseBranch) && element instanceof PsiIfStatement);
		}
		if (parent instanceof PsiWhileStatement)
		{
			return true;
		}
		if (parent instanceof PsiDoWhileStatement)
		{
			return true;
		}
		if (parent instanceof PsiForStatement)
		{
			final PsiForStatement forStatement = (PsiForStatement)parent;
			return element.equals(forStatement);
		}
		return parent instanceof PsiForeachStatement;
	}
}