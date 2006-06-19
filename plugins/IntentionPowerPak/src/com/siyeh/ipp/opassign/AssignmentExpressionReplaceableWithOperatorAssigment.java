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
package com.siyeh.ipp.opassign;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.EquivalenceChecker;
import com.siyeh.ipp.psiutils.ErrorUtil;
import com.siyeh.ipp.psiutils.SideEffectChecker;

class AssignmentExpressionReplaceableWithOperatorAssigment
        implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiAssignmentExpression)){
            return false;
        }
        final PsiAssignmentExpression assignment =
                (PsiAssignmentExpression) element;
        final PsiJavaToken sign = assignment.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!JavaTokenType.EQ.equals(tokenType)){
            return false;
        }
        final PsiExpression rhs = assignment.getRExpression();
        if(rhs == null){
            return false;
        }
        if(!(rhs instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryRhs = (PsiBinaryExpression) rhs;
        final PsiExpression rhsRhs = binaryRhs.getROperand();
        final PsiExpression rhsLhs = binaryRhs.getLOperand();
        if(rhsRhs == null){
            return false;
        }
        final PsiJavaToken operatorSign = binaryRhs.getOperationSign();
        final IElementType rhsTokenType = operatorSign.getTokenType();
        if(JavaTokenType.OROR.equals(rhsTokenType) ||
                JavaTokenType.ANDAND.equals(rhsTokenType)){
            return false;
        }
        final PsiExpression lhs = assignment.getLExpression();
        if(SideEffectChecker.mayHaveSideEffects(lhs)){
            return false;
        }
        if(!EquivalenceChecker.expressionsAreEquivalent(lhs, rhsLhs)){
            return false;
        }
        return !ErrorUtil.containsError(element);
    }
}
