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
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class ExpandBooleanPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiJavaToken)){
            return false;
        }
        final PsiJavaToken token = (PsiJavaToken) element;
        final PsiStatement containingStatement =
                PsiTreeUtil.getParentOfType(token,
                                            PsiStatement.class);
        if(containingStatement == null){
            return false;
        }
        if(isBooleanReturn(containingStatement)){
            return true;
        }
        if (!isBooleanAssignment(containingStatement)) {
            return false;
        }
        return !ErrorUtil.containsError(containingStatement);
    }

    public static boolean isBooleanReturn(PsiStatement containingStatement){
        if(!(containingStatement instanceof PsiReturnStatement)){
            return false;
        }
        final PsiReturnStatement returnStatement =
                (PsiReturnStatement) containingStatement;
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if(returnValue == null){
            return false;
        }
        if(returnValue instanceof PsiLiteralExpression){
            return false;
        }
        final PsiType returnType = returnValue.getType();
        if(returnType == null){
            return false;
        }
        return returnType.equals(PsiType.BOOLEAN);
    }

    public static boolean isBooleanAssignment(PsiStatement containingStatement){
        if(!(containingStatement instanceof PsiExpressionStatement)){
            return false;
        }
        final PsiExpressionStatement expressionStatement =
                (PsiExpressionStatement) containingStatement;
        final PsiExpression expression = expressionStatement.getExpression();
        if(!(expression instanceof PsiAssignmentExpression)){
            return false;
        }
        final PsiAssignmentExpression assignment =
                (PsiAssignmentExpression) expression;
        final PsiExpression rhs = assignment.getRExpression();
        if(rhs == null){
            return false;
        }
        if(rhs instanceof PsiLiteralExpression){
            return false;
        }
        final PsiType assignmentType = rhs.getType();
        if(assignmentType == null){
            return false;
        }
        return assignmentType.equals(PsiType.BOOLEAN);
    }
}
