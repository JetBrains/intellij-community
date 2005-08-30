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
package com.siyeh.ig.bitwise;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IncompatibleMaskInspection extends ExpressionInspection{
    public String getID(){
        return "IncompatibleBitwiseMaskOperation";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("incompatible.mask.operation.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BITWISE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) location;
        final PsiJavaToken operationSign = binaryExpression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if(tokenType.equals(JavaTokenType.EQEQ)){
            return InspectionGadgetsBundle.message("incompatible.mask.operation.problem.descriptor.always.false");
        } else{
            return InspectionGadgetsBundle.message("incompatible.mask.operation.problem.descriptor.always.true");
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new IncompatibleMaskVisitor();
    }

    private static class IncompatibleMaskVisitor extends BaseInspectionVisitor{

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression){
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            if(!ComparisonUtils.isEqualityComparison(expression)){
                return;
            }
            final PsiType expressionType = expression.getType();
            if(expressionType == null){
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            final PsiExpression strippedRhs = stripExpression(rhs);
            if(strippedRhs == null){
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression strippedLhs = stripExpression(lhs);
            if(strippedLhs == null){
                return;
            }
            if(isConstantMask(strippedLhs) &&
                       PsiUtil.isConstantExpression(strippedRhs)){
                if(isIncompatibleMask((PsiBinaryExpression) strippedLhs,
                                      strippedRhs)){
                    registerError(expression);
                }
            } else if(isConstantMask(strippedRhs) &&
                              PsiUtil.isConstantExpression(strippedLhs)){
                if(isIncompatibleMask((PsiBinaryExpression) strippedRhs,
                                      strippedLhs)){
                    registerError(expression);
                }
            }
        }
    }

    @Nullable
    private static PsiExpression stripExpression(PsiExpression exp){
        if(exp == null){
            return null;
        }
        if(exp instanceof PsiParenthesizedExpression){
            final PsiExpression body =
                    ((PsiParenthesizedExpression) exp).getExpression();
            return stripExpression(body);
        }
        return exp;
    }

    private static boolean isIncompatibleMask(PsiBinaryExpression maskExpression,
                                              PsiExpression constantExpression){
        final PsiJavaToken sign = maskExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        final Object constantValue =
                ConstantExpressionUtil.computeCastTo(constantExpression,
                                                     PsiType.LONG);
        if(constantValue == null){
            return false;
        }
        final long constantLongValue = (Long) constantValue;

        final PsiExpression maskRhs = maskExpression.getROperand();
        final PsiExpression maskLhs = maskExpression.getLOperand();
        final long constantMaskValue;
        if(PsiUtil.isConstantExpression(maskRhs)){
            final Object rhsValue =
                    ConstantExpressionUtil.computeCastTo(maskRhs, PsiType.LONG);
            if (rhsValue == null) {
                return false; // Might indeed be the case with "null" literal whoes constant value evaluates to null. Check out (a|null) case.
            }
            constantMaskValue = ((Long) rhsValue);
        } else{
            final Object lhsValue =
                    ConstantExpressionUtil.computeCastTo(maskLhs, PsiType.LONG);
            constantMaskValue = ((Long) lhsValue);
        }

        if(tokenType.equals(JavaTokenType.OR)){
            if((constantMaskValue | constantLongValue) != constantLongValue){
                return true;
            }
        }
        if(tokenType.equals(JavaTokenType.AND)){
            if((constantMaskValue | constantLongValue) != constantMaskValue){
                return true;
            }
        }
        return false;
    }

    private static boolean isConstantMask(PsiExpression expression){
        if(expression == null){
            return false;
        }
        if(!(expression instanceof PsiBinaryExpression)){
            return false;
        }

        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.OR) &&
                   !tokenType.equals(JavaTokenType.AND)){
            return false;
        }
        final PsiExpression rhs = binaryExpression.getROperand();
        if(PsiUtil.isConstantExpression(rhs)){
            return true;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        return PsiUtil.isConstantExpression(lhs);
    }
}
