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
package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NonNls;

class AssertTrueEqualsPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiMethodCallExpression)){
            return false;
        }
        if(ErrorUtil.containsError(element)){
            return false;
        }
        final PsiMethodCallExpression expression =
                (PsiMethodCallExpression) element;
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final int numArgs = args.length;
        if(numArgs < 1 || numArgs > 2){
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        if(!"assertTrue".equals(methodName)){
            return false;
        }
        if(numArgs == 1){
            return isEquality(args[0]);
        } else{
            return isEquality(args[1]);
        }
    }

    private static boolean isEquality(PsiExpression arg){
        if(arg instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExp = (PsiBinaryExpression) arg;
            final PsiJavaToken sign = binaryExp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            return JavaTokenType.EQEQ.equals(tokenType);
        } else if(arg instanceof PsiMethodCallExpression){
            final PsiMethodCallExpression expression =
                    (PsiMethodCallExpression) arg;
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList.getExpressions().length != 1){
                return false;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            return "equals".equals(methodName);
        }
        return false;
    }
}