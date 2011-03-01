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
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.InlineVariableFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnnecessaryLocalVariableInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean m_ignoreImmediatelyReturnedVariables = false;

    /** @noinspection PublicField*/
    public boolean m_ignoreAnnotatedVariables = false;

    @Override @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "redundant.local.variable.display.name");
    }

    @Override
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

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unnecessary.local.variable.problem.descriptor");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new InlineVariableFix();
    }

    @Override
    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryLocalVariableVisitor();
    }

    private class UnnecessaryLocalVariableVisitor
            extends BaseInspectionVisitor {

        @SuppressWarnings({"IfStatementWithIdenticalBranches"})
        @Override
        public void visitLocalVariable(
                @NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);

            if (m_ignoreAnnotatedVariables) {
                final PsiModifierList list = variable.getModifierList();
                if (list != null && list.getAnnotations().length > 0) {
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
            if (!variable.hasModifierProperty(PsiModifier.FINAL) &&
                    VariableAccessUtils.variableIsAssigned(variable,
                            containingScope, false)) {
                return false;
            }
            final PsiVariable initialization = (PsiVariable) referent;
            if (!initialization.hasModifierProperty(PsiModifier.FINAL) &&
                    VariableAccessUtils.variableIsAssigned(initialization,
                            containingScope, false)) {
                return false;
            }
            if (!initialization.hasModifierProperty(PsiModifier.FINAL)
                    && variable.hasModifierProperty(PsiModifier.FINAL)) {
                if (VariableAccessUtils.variableIsUsedInInnerClass(variable,
                        containingScope)) {
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
            if (!(nextStatement instanceof PsiReturnStatement)) {
                return false;
            }
            final PsiReturnStatement returnStatement =
                    (PsiReturnStatement) nextStatement;
            final PsiExpression returnValue = returnStatement.getReturnValue();
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
            if (!(nextStatement instanceof PsiThrowStatement)) {
                return false;
            }
            final PsiThrowStatement throwStatement =
                    (PsiThrowStatement) nextStatement;
            final PsiExpression returnValue = throwStatement.getException();
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
            final IElementType tokenType =
                    assignmentExpression.getOperationTokenType();
            if (tokenType != JavaTokenType.EQ) {
                return false;
            }
            final PsiExpression rhs = assignmentExpression.getRExpression();
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

            if (nextStatement instanceof PsiDeclarationStatement) {
                boolean referenceFound = false;
                for (PsiElement declaration : ((PsiDeclarationStatement)nextStatement).getDeclaredElements()) {
                    if (!(declaration instanceof PsiVariable)) {
                        continue;
                    }
                    final PsiExpression initializer = ((PsiVariable)declaration).getInitializer();
                    if (!referenceFound) {
                        if (initializer instanceof PsiReferenceExpression) {
                            final PsiElement referent = ((PsiReference)initializer).resolve();
                            if (variable.equals(referent)) {
                                referenceFound = true;
                                continue;
                            }
                        }
                    }
                    if (VariableAccessUtils.variableIsUsed(variable, initializer)) {
                        return false;
                    }
                }
                if (!referenceFound) {
                    return false;
                }
            }
            else if (nextStatement instanceof PsiTryStatement) {
                final PsiTryStatement tryStatement = (PsiTryStatement)nextStatement;
                final PsiResourceList resourceList = tryStatement.getResourceList();
                if (resourceList == null) {
                    return false;
                }
                boolean referenceFound = false;
                for (PsiResourceVariable resourceVariable : resourceList.getResourceVariables()) {
                    final PsiExpression initializer = resourceVariable.getInitializer();
                    if (!referenceFound) {
                        if (initializer instanceof PsiReferenceExpression) {
                            final PsiElement referent = ((PsiReferenceExpression)initializer).resolve();
                            if (variable.equals(referent)) {
                                referenceFound = true;
                                continue;
                            }
                        }
                    }
                    if (VariableAccessUtils.variableIsUsed(variable, initializer)) {
                        return false;
                    }
                }
                if (!referenceFound) {
                    return false;
                }
                if (VariableAccessUtils.variableIsUsed(variable, tryStatement.getTryBlock()) ||
                    VariableAccessUtils.variableIsUsed(variable, tryStatement.getFinallyBlock())) {
                    return false;
                }
                for (PsiCatchSection section : tryStatement.getCatchSections()) {
                    if (VariableAccessUtils.variableIsUsed(variable, section)) {
                        return false;
                    }
                }
            }
            else {
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
    }
}