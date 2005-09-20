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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class PointlessBitwiseExpressionInspection extends ExpressionInspection{
    public boolean m_ignoreExpressionsContainingConstants = false;

    static final Set<String> bitwiseTokens =
            new HashSet<String>(6);

    static{
        bitwiseTokens.add("&");
        bitwiseTokens.add("|");
        bitwiseTokens.add("^");
        bitwiseTokens.add("<<");
        bitwiseTokens.add(">>");
        bitwiseTokens.add(">>>");
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message("pointless.bitwise.expression.ignore.option.label"),                this, "m_ignoreExpressionsContainingConstants");
    }

    private final PointlessBitwiseFix fix = new PointlessBitwiseFix();

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("pointless.bitwise.expression.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BITWISE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
      return InspectionGadgetsBundle
        .message("pointless.bitwise.expression.problem.descriptor", calculateReplacementExpression((PsiExpression)location));
    }

    private String calculateReplacementExpression(PsiExpression expression){
        final PsiBinaryExpression exp = (PsiBinaryExpression) expression;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiJavaToken sign = exp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        assert rhs != null;
        final PsiType expressionType = exp.getType();
        if(tokenType.equals(JavaTokenType.AND)){
            if(isZero(lhs, expressionType) || isAllOnes(rhs, expressionType)){
                return lhs.getText();
            } else{
                return rhs.getText();
            }
        } else if(tokenType.equals(JavaTokenType.OR)){
            if(isZero(lhs, expressionType) || isAllOnes(rhs, expressionType)){
                return rhs.getText();
            } else{
                return lhs.getText();
            }
        } else if(tokenType.equals(JavaTokenType.XOR)){
            if(isAllOnes(lhs, expressionType)){
                return '~' + rhs.getText();
            } else if(isAllOnes(rhs, expressionType)){
                return '~' + lhs.getText();
            } else if(isZero(rhs, expressionType)){
                return lhs.getText();
            } else{
                return rhs.getText();
            }
        } else if(tokenType.equals(JavaTokenType.LTLT) ||
                tokenType.equals(JavaTokenType.GTGT) ||
                tokenType.equals(JavaTokenType.GTGTGT)){
            return lhs.getText();
        } else{
            return "";
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new PointlessBitwiseVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private class PointlessBitwiseFix extends InspectionGadgetsFix{
        public String getName(){
            return InspectionGadgetsBundle.message("pointless.bitwise.expression.simplify.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiExpression expression = (PsiExpression) descriptor
                    .getPsiElement();
            final String newExpression =
                    calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private class PointlessBitwiseVisitor extends BaseInspectionVisitor{

        public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression){
            super.visitBinaryExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            final String signText = sign.getText();
            if(!bitwiseTokens.contains(signText)){
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

            final PsiType rhsType = rhs.getType();
            if(rhsType == null){
                return;
            }
            if(rhsType.equals(PsiType.BOOLEAN) ||
                    rhsType.equalsToText("java.lang.Boolean")){
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiType lhsType = lhs.getType();
            if(lhsType == null){
                return;
            }
            if(lhsType.equals(PsiType.BOOLEAN) ||
                    lhsType.equalsToText("java.lang.Boolean")){
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            final boolean isPointless;
            if(tokenType.equals(JavaTokenType.AND)){
                isPointless = andExpressionIsPointless(lhs, rhs,
                                                       expressionType);
            } else if(tokenType.equals(JavaTokenType.OR)){
                isPointless = orExpressionIsPointless(lhs, rhs, expressionType);
            } else if(tokenType.equals(JavaTokenType.XOR)){
                isPointless = xorExpressionIsPointless(lhs, rhs,
                                                       expressionType);
            } else if(tokenType.equals(JavaTokenType.LTLT) ||
                    tokenType.equals(JavaTokenType.GTGT) ||
                    tokenType.equals(JavaTokenType.GTGTGT)){
                isPointless = shiftExpressionIsPointless(rhs, expressionType);
            } else{
                isPointless = false;
            }
            if(!isPointless){
                return;
            }
            registerError(expression);
        }

        private boolean andExpressionIsPointless(PsiExpression lhs,
                                                 PsiExpression rhs,
                                                 PsiType expressionType){
            return isZero(lhs, expressionType) || isZero(rhs, expressionType)
                   || isAllOnes(lhs, expressionType) || isAllOnes(rhs,
                    expressionType);
        }

        private boolean orExpressionIsPointless(PsiExpression lhs,
                                                PsiExpression rhs,
                                                PsiType expressionType){
            return isZero(lhs, expressionType) || isZero(rhs, expressionType)
                   || isAllOnes(lhs, expressionType) || isAllOnes(rhs,
                    expressionType);
        }

        private boolean xorExpressionIsPointless(PsiExpression lhs,
                                                 PsiExpression rhs,
                                                 PsiType expressionType){
            return isZero(lhs, expressionType) || isZero(rhs, expressionType)
                   || isAllOnes(lhs, expressionType) || isAllOnes(rhs,
                    expressionType);
        }

        private boolean shiftExpressionIsPointless(PsiExpression rhs,
                                                   PsiType expressionType){
            return isZero(rhs, expressionType);
        }
    }

    private boolean isZero(PsiExpression expression, PsiType expressionType){
        if(m_ignoreExpressionsContainingConstants
                && !(expression instanceof PsiLiteralExpression)){
            return false;
        }
        final Object value = ConstantExpressionUtil
                .computeCastTo(expression, expressionType);
        if(value == null){
            return false;
        }
        if(value instanceof Integer && ((Integer) value) == 0){
            return true;
        }
        if(value instanceof Long && ((Long) value) == 0L){
            return true;
        }
        if(value instanceof Short && ((Short) value) == 0){
            return true;
        }
        if(value instanceof Character && ((Character) value) == 0){
            return true;
        }
        return value instanceof Byte && ((Byte) value) == 0;
    }

    private boolean isAllOnes(PsiExpression expression, PsiType expressionType){
        if(m_ignoreExpressionsContainingConstants
                && !(expression instanceof PsiLiteralExpression)){
            return false;
        }
        final Object value = ConstantExpressionUtil
                .computeCastTo(expression, expressionType);
        if(value == null){
            return false;
        }
        if(value instanceof Integer && ((Integer) value) == 0xffffffff){
            return true;
        }
        if(value instanceof Long && ((Long) value) == 0xffffffffffffffffL){
            return true;
        }
        if(value instanceof Short && ((Short) value) == (short) 0xffff){
            return true;
        }
        if(value instanceof Character && ((Character) value) == (char) 0xffff){
            return true;
        }
        return value instanceof Byte && ((Byte) value) == (byte) 0xff;
    }
}