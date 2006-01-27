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
package com.siyeh.ig.assignment;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AssignmentToForLoopParameterInspection
        extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("assignment.to.for.loop.parameter.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ASSIGNMENT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("assignment.to.for.loop.parameter.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssignmentToForLoopParameterVisitor();
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("assignment.to.for.loop.parameter.check.foreach.option"), this,
                                              "m_checkForeachParameters");

    }

    public boolean m_checkForeachParameters = false;

    private class AssignmentToForLoopParameterVisitor
            extends BaseInspectionVisitor {

        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            checkForForLoopParam(lhs);
            checkForForeachLoopParam(lhs);
        }

        public void visitPrefixExpression(
                @NotNull PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            checkForForLoopParam(operand);
            checkForForeachLoopParam(operand);  //sensible due to autoboxing/unboxing
        }

        public void visitPostfixExpression(
                @NotNull PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            checkForForLoopParam(operand);
            checkForForeachLoopParam(operand);  //sensible due to autoboxing/unboxing
        }

        private void checkForForLoopParam(PsiExpression expression) {
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression ref =
                    (PsiReferenceExpression) expression;
            final PsiElement element = ref.resolve();
            if (!(element instanceof PsiLocalVariable)) {
                return;
            }
            final PsiLocalVariable variable = (PsiLocalVariable) element;
            final PsiDeclarationStatement decl =
                    (PsiDeclarationStatement) variable.getParent();
            if(decl == null) {
                return;
            }
            if (!(decl.getParent() instanceof PsiForStatement)) {
                return;
            }
            final PsiForStatement forStatement =
                    (PsiForStatement) decl.getParent();
            assert forStatement != null;
            final PsiStatement initialization = forStatement.getInitialization();
            if (initialization == null) {
                return;
            }
            if (!initialization.equals(decl)) {
                return;
            }
            if (!isInForStatementBody(expression, forStatement)) {
                return;
            }
            registerError(expression);
        }

        private void checkForForeachLoopParam(PsiExpression expression) {
            if (!m_checkForeachParameters) {
                return;
            }
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression ref =
                    (PsiReferenceExpression) expression;
            final PsiElement element = ref.resolve();
            if (!(element instanceof PsiParameter)) {
                return;
            }
            final PsiParameter parameter = (PsiParameter) element;
            if (!(parameter.getParent() instanceof PsiForeachStatement)) {
                return;
            }
            registerError(expression);
        }

        private boolean isInForStatementBody(PsiExpression expression,
                                                    PsiForStatement statement) {
            final PsiStatement body = statement.getBody();
            return body != null && PsiTreeUtil.isAncestor(body, expression, true);
        }
    }
}