/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnnecessaryParenthesesInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreClarifyingParentheses = false;

    @SuppressWarnings({"PublicField"})
    public boolean ignoreParenthesesOnConditionals = false;

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.parentheses.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unnecessary.parentheses.problem.descriptor");
    }

    @Override
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "unnecessary.parentheses.option"),
                "ignoreClarifyingParentheses");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "unnecessary.parentheses.conditional.option"),
                "ignoreParenthesesOnConditionals");
        return optionsPanel;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryParenthesesVisitor();
    }

    private class UnnecessaryParenthesesFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.parentheses.remove.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression expression =
                    (PsiExpression)descriptor.getPsiElement();
            ParenthesesUtils.removeParentheses(expression,
                    ignoreClarifyingParentheses);
        }
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryParenthesesFix();
    }

    private class UnnecessaryParenthesesVisitor
            extends BaseInspectionVisitor {

        @Override public void visitParenthesizedExpression(
                PsiParenthesizedExpression expression) {
            final PsiElement parent = expression.getParent();
            final PsiExpression child = expression.getExpression();
            if (child == null) {
                return;
            }
            if (!(parent instanceof PsiExpression) ||
                    parent instanceof PsiParenthesizedExpression) {
                registerError(expression);
                return;
            }
            final int parentPrecedence =
                    ParenthesesUtils.getPrecedence((PsiExpression)parent);
            final int childPrecedence = ParenthesesUtils.getPrecedence(child);
            if (parentPrecedence > childPrecedence) {
                if (ignoreClarifyingParentheses) {
                    if (parent instanceof PsiBinaryExpression &&
                            child instanceof PsiBinaryExpression) {
                        return;
                    } else if (child instanceof PsiInstanceOfExpression) {
                        return;
                    }
                }
                if (ignoreParenthesesOnConditionals) {
                    if (parent instanceof PsiConditionalExpression) {
                        final PsiConditionalExpression conditionalExpression =
                                (PsiConditionalExpression) parent;
                        final PsiExpression condition =
                                conditionalExpression.getCondition();
                        if (expression == condition) {
                            return;
                        }
                    }
                }
                registerError(expression);
                return;
            }
            if (parentPrecedence == childPrecedence) {
                if (!ParenthesesUtils.areParenthesesNeeded(expression,
                        ignoreClarifyingParentheses)) {
                    registerError(expression);
                    return;
                }
            }
            super.visitParenthesizedExpression(expression);
        }
    }
}