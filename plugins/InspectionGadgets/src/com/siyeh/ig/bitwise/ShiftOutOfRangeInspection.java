/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ShiftOutOfRangeInspection extends BaseInspection {

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "shift.operation.by.inappropriate.constant.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final Integer value = (Integer)infos[0];
        if(value.intValue() > 0){
            return InspectionGadgetsBundle.message(
                    "shift.operation.by.inappropriate.constant.problem.descriptor.too.large");
        } else{
            return InspectionGadgetsBundle.message(
                    "shift.operation.by.inappropriate.constant.problem.descriptor.negative");
        }
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ShiftOutOfRange();
    }

    private static class ShiftOutOfRange extends BaseInspectionVisitor{

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression){
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.LTLT) &&
                       !tokenType.equals(JavaTokenType.GTGT) &&
                       !tokenType.equals(JavaTokenType.GTGTGT)){
                return;
            }
            final PsiType expressionType = expression.getType();
            if(expressionType == null){
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if(rhs == null){
                return;
            }
            if(!PsiUtil.isConstantExpression(rhs)){
                return;
            }
            final Integer valueObject =
                    (Integer) ConstantExpressionUtil.computeCastTo(rhs,
                            PsiType.INT);
            if(valueObject == null){
                return;
            }
            final int value = valueObject.intValue();
            if(expressionType.equals(PsiType.LONG)){
                if(value < 0 || value > 63){
                    registerError(sign, valueObject);
                }
            }
            if(expressionType.equals(PsiType.INT)){
                if(value < 0 || value > 31){
                    registerError(sign, valueObject);
                }
            }
        }
    }
}