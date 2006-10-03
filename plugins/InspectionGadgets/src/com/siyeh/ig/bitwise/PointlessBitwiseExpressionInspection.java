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
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class PointlessBitwiseExpressionInspection extends ExpressionInspection{

    /** @noinspection PublicField*/
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

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "pointless.bitwise.expression.display.name");
    }

    @NotNull
    public String getGroupDisplayName(){
        return GroupNames.BITWISE_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final String replacementExpression =
                calculateReplacementExpression((PsiExpression)infos[0]);
        return InspectionGadgetsBundle.message(
                "pointless.bitwise.expression.problem.descriptor",
                replacementExpression);
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "pointless.bitwise.expression.ignore.option"),
                this, "m_ignoreExpressionsContainingConstants");
    }

    String calculateReplacementExpression(PsiExpression expression){
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        assert rhs != null;
        if(tokenType.equals(JavaTokenType.AND)){
            if(isZero(lhs) || isAllOnes(rhs)){
                return lhs.getText();
            } else{
                return rhs.getText();
            }
        } else if(tokenType.equals(JavaTokenType.OR)){
            if(isZero(lhs) || isAllOnes(rhs)){
                return rhs.getText();
            } else{
                return lhs.getText();
            }
        } else if(tokenType.equals(JavaTokenType.XOR)){
            if(isAllOnes(lhs)){
                return '~' + rhs.getText();
            } else if(isAllOnes(rhs)){
                return '~' + lhs.getText();
            } else if(isZero(rhs)){
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
        return new PointlessBitwiseFix();
    }

    private class PointlessBitwiseFix extends InspectionGadgetsFix{

        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message(
                    "pointless.bitwise.expression.simplify.quickfix");
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
                isPointless = andExpressionIsPointless(lhs, rhs);
            } else if(tokenType.equals(JavaTokenType.OR)){
                isPointless = orExpressionIsPointless(lhs, rhs);
            } else if(tokenType.equals(JavaTokenType.XOR)){
                isPointless = xorExpressionIsPointless(lhs, rhs);
            } else if(tokenType.equals(JavaTokenType.LTLT) ||
                    tokenType.equals(JavaTokenType.GTGT) ||
                    tokenType.equals(JavaTokenType.GTGTGT)){
                isPointless = shiftExpressionIsPointless(rhs);
            } else{
                isPointless = false;
            }
            if(!isPointless){
                return;
            }
            registerError(expression, expression);
        }

        private boolean andExpressionIsPointless(PsiExpression lhs,
                                                 PsiExpression rhs) {
            return isZero(lhs) || isZero(rhs)
                   || isAllOnes(lhs) || isAllOnes(rhs);
        }

        private boolean orExpressionIsPointless(PsiExpression lhs,
                                                PsiExpression rhs) {
            return isZero(lhs) || isZero(rhs)
                   || isAllOnes(lhs) || isAllOnes(rhs);
        }

        private boolean xorExpressionIsPointless(PsiExpression lhs,
                                                 PsiExpression rhs) {
            return isZero(lhs) || isZero(rhs)
                   || isAllOnes(lhs) || isAllOnes(rhs);
        }

        private boolean shiftExpressionIsPointless(PsiExpression rhs) {
            return isZero(rhs);
        }
    }

    private boolean isZero(PsiExpression expression){
        if(m_ignoreExpressionsContainingConstants
                && !(expression instanceof PsiLiteralExpression)){
            return false;
        }
        return ExpressionUtils.isZero(expression);
    }

    private boolean isAllOnes(PsiExpression expression){
        if(m_ignoreExpressionsContainingConstants
                && !(expression instanceof PsiLiteralExpression)){
            return false;
        }
        final PsiType expressionType = expression.getType();
        final Object value =
                ConstantExpressionUtil.computeCastTo(expression, expressionType);
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