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
package com.siyeh.ig.dataflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.InlineVariableFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.psiutils.VariableAssignedVisitor;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class UnnecessaryLocalVariableInspection extends ExpressionInspection {

    /** @noinspection PublicField*/
    public boolean m_ignoreImmediatelyReturnedVariables = false;

    /** @noinspection PublicField*/
    public boolean m_ignoreAnnotatedVariables = false;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "redundant.local.variable.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.DATA_FLOW_ISSUES;
    }

    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "redundant.local.variable.ignore.option"),
                "m_ignoreImmediatelyReturnedVariables");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "redundant.local.variable.annotation.option"),
                "m_ignoreAnnotatedVariables");
        return optionsPanel;
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unnecessary.local.variable.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryLocalVariableVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new InlineVariableFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    private class UnnecessaryLocalVariableVisitor
            extends BaseInspectionVisitor {

        public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            final PsiModifierList modifierList = variable.getModifierList();
            if (m_ignoreAnnotatedVariables) {
                final PsiAnnotation[] annotations =
                        modifierList.getAnnotations();
                if (annotations.length > 0) {
                    return;
                }
            }
            if (isCopyVariable(variable)) {
                registerVariableError(variable);
            } else if (!m_ignoreImmediatelyReturnedVariables &&
                               isImmediatelyReturned(variable)) {
                registerVariableError(variable);
            } else if (!m_ignoreImmediatelyReturnedVariables &&
                    isImmediatelyThrown(variable)) {
                registerVariableError(variable);
            } else if (isImmediatelyAssigned(variable)) {
                registerVariableError(variable);
            } else if (isImmediatelyAssignedAsDeclaration(variable)) {
                registerVariableError(variable);
            }
        }

        private boolean isCopyVariable(PsiVariable variable) {
            final PsiExpression initializer = variable.getInitializer();
            if (initializer == null) {
                return false;
            }
            if (!(initializer instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression reference =
                    (PsiReferenceExpression)initializer;
            final PsiElement referent = reference.resolve();
            if (referent == null) {
                return false;
            }
            if (!(referent instanceof PsiLocalVariable ||
                    referent instanceof PsiParameter)) {
                return false;
            }
            final PsiCodeBlock containingScope =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (containingScope == null) {
                return false;
            }
            final VariableAssignedVisitor visitor =
                    new VariableAssignedVisitor(variable);
            containingScope.accept(visitor);
            if (visitor.isAssigned()) {
                return false;
            }
            final PsiVariable initialization = (PsiVariable) referent;
            final VariableAssignedVisitor visitor2 =
                    new VariableAssignedVisitor(initialization);
            containingScope.accept(visitor2);
            if (visitor2.isAssigned()) {
                return false;
            }
            if (!initialization.hasModifierProperty(PsiModifier.FINAL)
                    && variable.hasModifierProperty(PsiModifier.FINAL)) {
                if (variableIsUsedInInnerClass(containingScope, variable)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isImmediatelyReturned(PsiVariable variable) {
            final PsiCodeBlock containingScope =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (containingScope == null) {
                return false;
            }
            final PsiDeclarationStatement declarationStatement =
                    PsiTreeUtil.getParentOfType(variable,
                                                PsiDeclarationStatement.class);
            if (declarationStatement == null) {
                return false;
            }
            PsiStatement nextStatement = null;
            final PsiStatement[] statements = containingScope.getStatements();
            for (int i = 0; i < statements.length - 1; i++) {
                if (statements[i].equals(declarationStatement)) {
                    nextStatement = statements[i + 1];
                }
            }
            if (nextStatement == null) {
                return false;
            }
            if (!(nextStatement instanceof PsiReturnStatement)) {
                return false;
            }
            final PsiReturnStatement returnStatement =
                    (PsiReturnStatement) nextStatement;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            if (returnValue == null) {
                return false;
            }
            if (!(returnValue instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiElement referent = ((PsiReference) returnValue).resolve();
            return !(referent == null || !referent.equals(variable));
        }

        private boolean isImmediatelyThrown(PsiVariable variable) {
            final PsiCodeBlock containingScope =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (containingScope == null) {
                return false;
            }
            final PsiDeclarationStatement declarationStatement =
                    PsiTreeUtil.getParentOfType(variable,
                                                PsiDeclarationStatement.class);
            if (declarationStatement == null) {
                return false;
            }
            PsiStatement nextStatement = null;
            final PsiStatement[] statements = containingScope.getStatements();
            for (int i = 0; i < statements.length - 1; i++) {
                if (statements[i].equals(declarationStatement)) {
                    nextStatement = statements[i + 1];
                }
            }
            if (nextStatement == null) {
                return false;
            }
            if (!(nextStatement instanceof PsiThrowStatement)) {
                return false;
            }
            final PsiThrowStatement throwStatement =
                    (PsiThrowStatement) nextStatement;
            final PsiExpression returnValue = throwStatement.getException();
            if (returnValue == null) {
                return false;
            }
            if (!(returnValue instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiElement referent = ((PsiReference) returnValue).resolve();
            return !(referent == null || !referent.equals(variable));
        }

        private boolean isImmediatelyAssigned(PsiVariable variable) {
            final PsiCodeBlock containingScope =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (containingScope == null) {
                return false;
            }
            final PsiDeclarationStatement declarationStatement =
                    PsiTreeUtil.getParentOfType(variable,
                                                PsiDeclarationStatement.class);
            if (declarationStatement == null) {
                return false;
            }
            PsiStatement nextStatement = null;
            int followingStatementNumber = 0;
            final PsiStatement[] statements = containingScope.getStatements();
            for (int i = 0; i < statements.length - 1; i++) {
                if (statements[i].equals(declarationStatement)) {
                    nextStatement = statements[i + 1];
                    followingStatementNumber = i + 2;
                }
            }
            if (nextStatement == null) {
                return false;
            }
            if (!(nextStatement instanceof PsiExpressionStatement)) {
                return false;
            }
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement) nextStatement;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            if (!(expression instanceof PsiAssignmentExpression)) {
                return false;
            }
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression)expression;
            final PsiExpression rhs = assignmentExpression.getRExpression();
            if (rhs == null) {
                return false;
            }
            if (!(rhs instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression reference =
                    (PsiReferenceExpression)rhs;
            final PsiElement referent = reference.resolve();
            if (referent == null || !referent.equals(variable)) {
                return false;
            }
            final PsiExpression lhs = assignmentExpression.getLExpression();
            if (lhs instanceof PsiArrayAccessExpression) {
                return false;
            }
            if (VariableAccessUtils.variableIsUsed(variable, lhs)) {
                return false;
            }
            for (int i = followingStatementNumber; i < statements.length; i++) {
                if (VariableAccessUtils.variableIsUsed(variable,
                        statements[i])) {
                    return false;
                }
            }
            return true;
        }

        private boolean isImmediatelyAssignedAsDeclaration(
                PsiVariable variable) {
            final PsiCodeBlock containingScope =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (containingScope == null) {
                return false;
            }
            final PsiDeclarationStatement declarationStatement =
                    PsiTreeUtil.getParentOfType(variable,
                                                PsiDeclarationStatement.class);
            if (declarationStatement == null) {
                return false;
            }
            PsiStatement nextStatement = null;
            int followingStatementNumber = 0;
            final PsiStatement[] statements = containingScope.getStatements();
            for (int i = 0; i < statements.length - 1; i++) {
                if (statements[i].equals(declarationStatement)) {
                    nextStatement = statements[i + 1];
                    followingStatementNumber = i + 2;
                }
            }
            if (nextStatement == null) {
                return false;
            }
            if (!(nextStatement instanceof PsiDeclarationStatement)) {
                return false;
            }
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement) nextStatement;
            final PsiElement[] declarations = declaration.getDeclaredElements();
            if (declarations.length != 1) {
                return false;
            }
            if (!(declarations[0] instanceof PsiVariable)) {
                return false;
            }
            final PsiExpression rhs =
                    ((PsiVariable) declarations[0]).getInitializer();
            if (rhs == null) {
                return false;
            }
            if (!(rhs instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiElement referent = ((PsiReference) rhs).resolve();
            if (referent == null || !referent.equals(variable)) {
                return false;
            }
            for (int i = followingStatementNumber; i < statements.length; i++) {
                if (VariableAccessUtils.variableIsUsed(variable,
                        statements[i])) {
                    return false;
                }
            }
            return true;
        }

        private boolean variableIsUsedInInnerClass(PsiCodeBlock block,
                                                   PsiVariable variable) {
            final VariableUsedInInnerClassVisitor visitor =
                    new VariableUsedInInnerClassVisitor(variable);
            block.accept(visitor);
            return visitor.isUsedInInnerClass();
        }
    }
}