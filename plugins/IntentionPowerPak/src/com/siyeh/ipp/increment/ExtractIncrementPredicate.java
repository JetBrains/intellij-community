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
package com.siyeh.ipp.increment;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class ExtractIncrementPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiPrefixExpression) &&
                !(element instanceof PsiPostfixExpression)){
            return false;
        }
        final PsiJavaToken sign;
        if(element instanceof PsiPostfixExpression){
            sign = ((PsiPostfixExpression) element).getOperationSign();
        } else{
            sign = ((PsiPrefixExpression) element).getOperationSign();
        }
        final IElementType tokenType = sign.getTokenType();
        if(!JavaTokenType.PLUSPLUS.equals(tokenType) &&
                !JavaTokenType.MINUSMINUS.equals(tokenType)){
            return false;
        }
        final PsiElement parent = element.getParent();
        if(parent instanceof PsiExpressionStatement){
            return false;
        }
        final PsiStatement containingStatement =
                PsiTreeUtil.getParentOfType(element, PsiStatement.class);
        if((containingStatement instanceof PsiReturnStatement ||
                containingStatement instanceof PsiThrowStatement) &&
                element instanceof PsiPostfixExpression){
            return false;
        }
        if (containingStatement == null) {
            return false;
        }
        return !ErrorUtil.containsError(element);
    }
}