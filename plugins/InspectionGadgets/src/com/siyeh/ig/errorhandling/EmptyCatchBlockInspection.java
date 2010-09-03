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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EmptyCatchBlockInspection extends BaseInspection {

    /** @noinspection PublicField */
    public boolean m_includeComments = true;
    /** @noinspection PublicField */
    public boolean m_ignoreTestCases = true;
    /** @noinspection PublicField */
    public boolean m_ignoreIgnoreParameter = true;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "empty.catch.block.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "empty.catch.block.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "empty.catch.block.comments.option"), "m_includeComments");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "empty.catch.block.ignore.option"), "m_ignoreTestCases");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "empty.catch.block.ignore.ignore.option"),
                "m_ignoreIgnoreParameter");
        return optionsPanel;
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new EmptyCatchBlockFix();
    }

    private static class EmptyCatchBlockFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "rename.catch.parameter.to.ignored");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiCatchSection)) {
                return;
            }
            final PsiCatchSection catchSection = (PsiCatchSection)parent;
            final PsiParameter parameter = catchSection.getParameter();
            if (parameter == null) {
                return;
            }
            final PsiIdentifier identifier = parameter.getNameIdentifier();
            if (identifier == null) {
                return;
            }
            final PsiManager manager = element.getManager();
          final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
            final PsiIdentifier newIdentifier =
                    factory.createIdentifier("ignored");
            identifier.replace(newIdentifier);
        }
    }


    public BaseInspectionVisitor buildVisitor() {
        return new EmptyCatchBlockVisitor();
    }

    private class EmptyCatchBlockVisitor extends BaseInspectionVisitor {

        @Override public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            if (JspPsiUtil.isInJspFile(statement.getContainingFile())) {
                return;
            }
            if (m_ignoreTestCases &&
                    TestUtils.isPartOfJUnitTestMethod(statement)) {
                return;
            }
            final PsiCatchSection[] catchSections =
                    statement.getCatchSections();
            for (final PsiCatchSection section : catchSections) {
                checkCatchSection(section);
            }
        }

        private void checkCatchSection(PsiCatchSection section) {
            final PsiCodeBlock block = section.getCatchBlock();
            if (block == null || !catchBlockIsEmpty(block)) {
                return;
            }
            final PsiParameter parameter = section.getParameter();
            if (parameter == null) {
                return;
            }
            final PsiIdentifier identifier = parameter.getNameIdentifier();
            if (identifier == null) {
                return;
            }
            @NonNls final String parameterName =
                    parameter.getName();
            if (m_ignoreIgnoreParameter &&
                    ("ignore".equals(parameterName) ||
                            "ignored".equals(parameterName))) {
                return;
            }
            final PsiElement catchToken = section.getFirstChild();
            if (catchToken == null) {
                return;
            }
            registerError(catchToken);
        }

        private boolean catchBlockIsEmpty(PsiCodeBlock block) {
            if (m_includeComments) {
                final PsiElement[] children = block.getChildren();
                for (final PsiElement child : children) {
                    if (child instanceof PsiComment ||
                            child instanceof PsiStatement) {
                        return false;
                    }
                }
                return true;
            } else {
                final PsiStatement[] statements = block.getStatements();
                return statements.length == 0;
            }
        }
    }
}