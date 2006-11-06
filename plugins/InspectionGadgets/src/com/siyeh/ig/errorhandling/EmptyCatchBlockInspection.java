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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class EmptyCatchBlockInspection extends StatementInspection {

    /** @noinspection PublicField */
    public boolean m_includeComments = true;
    /** @noinspection PublicField */
    public boolean m_ignoreTestCases = true;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "empty.catch.block.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
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
        return optionsPanel;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptyCatchBlockVisitor();
    }

    private class EmptyCatchBlockVisitor extends StatementInspectionVisitor {

        public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
          if (PsiUtil.isInJspFile(statement.getContainingFile())) {
            return;
          }
            if (m_ignoreTestCases &&
                    TestUtils.isPartOfJUnitTestMethod(statement)) {
                return;
            }
            final PsiCatchSection[] catchSections =
                    statement.getCatchSections();
            for (final PsiCatchSection section : catchSections) {
                final PsiCodeBlock block = section.getCatchBlock();
                if (block != null && catchBlockIsEmpty(block)) {
                    final PsiElement catchToken = section.getFirstChild();
                    if (catchToken != null) {
                        registerError(catchToken);
                    }
                }
            }
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