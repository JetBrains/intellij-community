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
package com.siyeh.ipp.shift;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class ShiftByLiteralPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(element instanceof PsiBinaryExpression){
            return isBinaryShiftByLiteral((PsiBinaryExpression) element);
        }
        if(element instanceof PsiAssignmentExpression){
            return isAssignmentShiftByLiteral(
                    (PsiAssignmentExpression) element);
        } else{
            return false;
        }
    }

    private static boolean isAssignmentShiftByLiteral(
            PsiAssignmentExpression expression){
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.LTLTEQ) &&
                !tokenType.equals(JavaTokenType.GTGTEQ)){
            return false;
        }
        final PsiExpression lhs = expression.getLExpression();
        final PsiType lhsType = lhs.getType();
        if(lhsType == null){
            return false;
        }
        if(!ShiftUtils.isIntegral(lhsType)){
            return false;
        }
        final PsiExpression rhs = expression.getRExpression();
        if(rhs == null){
            return false;
        }
        return ShiftUtils.isIntLiteral(rhs);
    }

    private static boolean isBinaryShiftByLiteral(
            PsiBinaryExpression expression){
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.LTLT) &&
                !tokenType.equals(JavaTokenType.GTGT)){
            return false;
        }
        final PsiExpression lOperand = expression.getLOperand();
        final PsiType lhsType = lOperand.getType();
        if(!ShiftUtils.isIntegral(lhsType)){
            return false;
        }
        final PsiExpression rhs = expression.getROperand();
        return ShiftUtils.isIntLiteral(rhs);
    }
}
