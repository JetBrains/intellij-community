/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekekrs
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
package com.siyeh.ipp.conditional;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class ReplaceConditionalWithIfPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(element instanceof PsiReturnStatement){
            if(!isReturnOfConditional((PsiReturnStatement)element)){
                return false;
            }
            return !ErrorUtil.containsError(element);
        }
        if(element instanceof PsiExpressionStatement){
            if(!isAssignmentToConditional((PsiExpressionStatement)element)){
                return false;
            }
            return !ErrorUtil.containsError(element);
        }
        if(element instanceof PsiDeclarationStatement){
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement)element;
            if(!isDeclarationOfConditional(declarationStatement)){
                return false;
            }
            return !ErrorUtil.containsError(element);
        } else{
            return false;
        }
    }

    private static boolean isDeclarationOfConditional(
            PsiDeclarationStatement declarationStatement){
        final PsiElement[] variables =
                declarationStatement.getDeclaredElements();
        if(variables.length != 1){
            return false;
        }
        if(!(variables[0] instanceof PsiLocalVariable)){
            return false;
        }
        final PsiLocalVariable variable = (PsiLocalVariable) variables[0];
        final PsiExpression initializer = variable.getInitializer();
        if(initializer == null){
            return false;
        }
        if(!(initializer instanceof PsiConditionalExpression)){
            return false;
        }
        final PsiConditionalExpression condition =
                (PsiConditionalExpression) initializer;
        return condition.getThenExpression() != null &&
                condition.getElseExpression() != null;
    }

    private static boolean isAssignmentToConditional(
            PsiExpressionStatement expressionStatement){
        final PsiExpression expression = expressionStatement.getExpression();
        if(!(expression instanceof PsiAssignmentExpression)){
            return false;
        }
        final PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression) expression;
        final PsiExpression rhs = assignmentExpression.getRExpression();
        if(rhs == null){
            return false;
        }
        if(!(rhs instanceof PsiConditionalExpression)){
            return false;
        }
        final PsiConditionalExpression condition =
                (PsiConditionalExpression) rhs;
        return condition.getThenExpression() != null &&
                condition.getElseExpression() != null;
    }

    private static boolean isReturnOfConditional(
            PsiReturnStatement returnStatement){
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if(returnValue == null){
            return false;
        }
        if(!(returnValue instanceof PsiConditionalExpression)){
            return false;
        }
        final PsiConditionalExpression condition =
                (PsiConditionalExpression) returnValue;
        return condition.getThenExpression() != null &&
                condition.getElseExpression() != null;
    }
}