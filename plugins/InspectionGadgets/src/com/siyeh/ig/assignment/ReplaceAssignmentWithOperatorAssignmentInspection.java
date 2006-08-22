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
package com.siyeh.ig.assignment;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class ReplaceAssignmentWithOperatorAssignmentInspection
        extends ExpressionInspection{

    /** @noinspection PublicField*/
    public boolean ignoreLazyOperators = true;

    /** @noinspection PublicField*/
    public boolean ignoreObscureOperators = false;

    public String getID(){
        return "AssignmentReplaceableWithOperatorAssignment";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "assignment.replaceable.with.operator.assignment.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.ASSIGNMENT_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression)infos[0];
        return InspectionGadgetsBundle.message(
                "assignment.replaceable.with.operator.assignment.problem.descriptor",
                calculateReplacementExpression(assignmentExpression));
    }

    @Nullable
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "assignment.replaceable.with.operator.assignment.ignore.conditional.operators.option"),
                "ignoreLazyOperators");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "assignment.replaceable.with.operator.assignment.ignore.obscure.operators.option"),
                "ignoreObscureOperators");
        return optionsPanel;
    }

    static String calculateReplacementExpression(
            PsiAssignmentExpression expression){
        final PsiBinaryExpression rhs =
                (PsiBinaryExpression) expression.getRExpression();
        final PsiExpression lhs = expression.getLExpression();
        assert rhs != null;
        final PsiJavaToken sign = rhs.getOperationSign();
        final PsiExpression rhsRhs = rhs.getROperand();
        assert rhsRhs != null;
        String signText = sign.getText();
        if("&&".equals(signText)){
            signText = "&";
        } else if("||".equals(signText)){
            signText = "|";
        }
        return lhs.getText() + ' ' + signText + "= " + rhsRhs.getText();
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ReplaceAssignmentWithOperatorAssignmentVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new ReplaceAssignmentWithOperatorAssignmentFix(
                (PsiAssignmentExpression) location);
    }

    private static class ReplaceAssignmentWithOperatorAssignmentFix
            extends InspectionGadgetsFix{

        private final String m_name;

        private ReplaceAssignmentWithOperatorAssignmentFix(
                PsiAssignmentExpression expression){
            super();
            final PsiBinaryExpression rhs =
                    (PsiBinaryExpression) expression.getRExpression();
            assert rhs != null;
            final PsiJavaToken sign = rhs.getOperationSign();
            String signText = sign.getText();
            if("&&".equals(signText)){
                signText = "&";
            } else if("||".equals(signText)){
                signText = "|";
            }
            m_name = InspectionGadgetsBundle.message(
                    "assignment.replaceable.with.operator.replace.quickfix",
                    signText, Character.valueOf('='));
        }

        @NotNull
        public String getName(){
            return m_name;
        }

        public void doFix(@NotNull Project project,
                          ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement element = descriptor.getPsiElement();
            if(!(element instanceof PsiAssignmentExpression)){
                return;
            }
            final PsiAssignmentExpression expression =
                    (PsiAssignmentExpression) element;
            final String newExpression =
                    calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }

    }

    private class ReplaceAssignmentWithOperatorAssignmentVisitor
            extends BaseInspectionVisitor{

        public void visitAssignmentExpression(@NotNull
                PsiAssignmentExpression assignment){
            super.visitAssignmentExpression(assignment);
            final PsiJavaToken sign = assignment.getOperationSign();
            final IElementType assignmentTokenType = sign.getTokenType();
            if(!assignmentTokenType.equals(JavaTokenType.EQ)){
                return;
            }
            final PsiExpression lhs = assignment.getLExpression();
            final PsiExpression rhs = assignment.getRExpression();
            if(!(rhs instanceof PsiBinaryExpression)){
                return;
            }
            final PsiBinaryExpression binaryRhs = (PsiBinaryExpression) rhs;
            if(!(binaryRhs.getROperand() != null)){
                return;
            }
            final IElementType expressionTokenType =
                    binaryRhs.getOperationTokenType();
            if (expressionTokenType.equals(JavaTokenType.EQEQ)) {
                return;
            }
            if (ignoreLazyOperators) {
                if (expressionTokenType.equals(JavaTokenType.ANDAND) ||
                        expressionTokenType.equals(JavaTokenType.OROR)) {
                    return;
                }
            }
            if (ignoreObscureOperators) {
                if (expressionTokenType.equals(JavaTokenType.XOR) ||
                        expressionTokenType.equals(JavaTokenType.PERC)) {
                    return;
                }
            }
            final PsiExpression lOperand = binaryRhs.getLOperand();
            if(SideEffectChecker.mayHaveSideEffects(lhs)){
                return;
            }
            if(!EquivalenceChecker.expressionsAreEquivalent(lhs, lOperand)) {
                return;
            }
            registerError(assignment, assignment);
        }
    }
}