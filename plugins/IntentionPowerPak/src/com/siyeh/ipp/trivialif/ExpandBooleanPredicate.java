/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
        final PsiStatement containingStatement =
                PsiTreeUtil.getParentOfType(element,
                                            PsiStatement.class);
        if(containingStatement == null){
            return false;
        }
        return isBooleanReturn(containingStatement) ||
                isBooleanAssignment(containingStatement);
    }

    public static boolean isBooleanReturn(PsiStatement containingStatement){
        if(!(containingStatement instanceof PsiReturnStatement)){
            return false;
        }
        final PsiReturnStatement returnStatement =
                (PsiReturnStatement) containingStatement;
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue == null || returnValue instanceof PsiLiteralExpression) {
            return false;
        }
        final PsiType returnType = returnValue.getType();
        if (!PsiType.BOOLEAN.equals(returnType)) {
            return false;
        }
        return !ErrorUtil.containsError(returnValue);
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
        if (rhs == null || rhs instanceof PsiLiteralExpression) {
            return false;
        }
        final PsiType assignmentType = rhs.getType();
        if (!PsiType.BOOLEAN.equals(assignmentType)) {
            return false;
        }
        return !ErrorUtil.containsError(rhs);
    }
}
