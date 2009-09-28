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
package com.siyeh.ipp.equality;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class ObjectEqualityPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression expression = (PsiBinaryExpression) element;
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.NE) &&
                !tokenType.equals(JavaTokenType.EQEQ)){
            return false;
        }
        final PsiExpression lhs = expression.getLOperand();
	    final String lhsText = lhs.getText();
	    if (PsiKeyword.NULL.equals(lhsText)) {
		    return false;
	    }
        final PsiType lhsType = lhs.getType();
        if(lhsType == null){
            return false;
        }
        final PsiExpression rhs = expression.getROperand();
        if(rhs == null){
            return false;
        }
	    final String rhsText = rhs.getText();
	    if (PsiKeyword.NULL.equals(rhsText)) {
		    return false;
	    }
        final PsiType rhsType = rhs.getType();
        if(rhsType == null){
            return false;
        }
	    if (TypeConversionUtil.isPrimitiveAndNotNull(lhsType) ||
			    TypeConversionUtil.isPrimitiveAndNotNull(rhsType)) {
		    return false;
	    }
        if (rhsType instanceof PsiClassType) {
            final PsiClassType rhsClassType = (PsiClassType)rhsType;
            final PsiClass rhsClass = rhsClassType.resolve();
            if (rhsClass != null && rhsClass.isEnum()) {
                return false;
            }
        }
        if (lhsType instanceof PsiClassType) {
            final PsiClassType lhsClassType = (PsiClassType)lhsType;
            final PsiClass lhsClass = lhsClassType.resolve();
            if (lhsClass != null && lhsClass.isEnum()) {
                return false;
            }
        }
        return !ErrorUtil.containsError(element);
    }
}