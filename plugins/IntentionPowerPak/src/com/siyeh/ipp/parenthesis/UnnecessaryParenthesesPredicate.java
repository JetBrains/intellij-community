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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

class UnnecessaryParenthesesPredicate implements PsiElementPredicate{

	public boolean satisfiedBy(PsiElement element){
		if(!(element instanceof PsiParenthesizedExpression)){
			return false;
		}
		if(ErrorUtil.containsError(element)){
			return false;
		}
		final PsiParenthesizedExpression expression =
				(PsiParenthesizedExpression) element;
		final PsiElement parent = expression.getParent();
		if(!(parent instanceof PsiExpression)){
			return true;
		}
		final PsiExpression body = expression.getExpression();
		if(body instanceof PsiParenthesizedExpression){
			return true;
		}
		final int parentPrecendence =
                ParenthesesUtils.getPrecendence((PsiExpression) parent);
		final int childPrecendence = ParenthesesUtils.getPrecendence(body);
		if(parentPrecendence > childPrecendence){
			return true;
		} else if(parentPrecendence == childPrecendence){
			if(parent instanceof PsiBinaryExpression &&
					body instanceof PsiBinaryExpression){
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) parent;
                final PsiJavaToken parentSign =
                        binaryExpression.getOperationSign();
				final IElementType parentOperator = parentSign.getTokenType();
				final PsiJavaToken childSign =
						((PsiBinaryExpression) body).getOperationSign();
				final IElementType childOperator = childSign.getTokenType();
                if(!parentOperator.equals(childOperator)){
                    return false;
                }
                final PsiType parentType = binaryExpression.getType();
                final PsiType bodyType = body.getType();
                return parentType != null && parentType.equals(bodyType);
            } else{
				return false;
			}
		} else{
			return false;
		}
	}
}