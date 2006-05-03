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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NonNls;

class MergeElseIfPredicate implements PsiElementPredicate{

	public boolean satisfiedBy(PsiElement element){
		if(!(element instanceof PsiJavaToken)){
			return false;
		}
		@NonNls final String text = element.getText();
		if(!PsiKeyword.ELSE.equals(text)){
			return false;
		}
		final PsiJavaToken token = (PsiJavaToken) element;
		final PsiElement parent = token.getParent();
		if(!(parent instanceof PsiIfStatement)){
			return false;
		}
		final PsiIfStatement ifStatement = (PsiIfStatement) parent;
		if(ErrorUtil.containsError(ifStatement)){
			return false;
		}
		final PsiStatement thenBranch = ifStatement.getThenBranch();
		final PsiStatement elseBranch = ifStatement.getElseBranch();
		if(thenBranch == null){
			return false;
		}
		if(!(elseBranch instanceof PsiBlockStatement)){
			return false;
		}
		final PsiCodeBlock block =
				((PsiBlockStatement) elseBranch).getCodeBlock();
		final PsiStatement[] statements = block.getStatements();
		return statements.length == 1 &&
				statements[0] instanceof PsiIfStatement;
	}
}