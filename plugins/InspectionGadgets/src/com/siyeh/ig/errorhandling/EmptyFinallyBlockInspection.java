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

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class EmptyFinallyBlockInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "empty.finally.block.display.name");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "empty.finally.block.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptyFinallyBlockVisitor();
    }

    private static class EmptyFinallyBlockVisitor
            extends BaseInspectionVisitor {

        @Override public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            if (PsiUtil.isInJspFile(statement.getContainingFile())) {
                return;
            }
            final PsiCodeBlock finallyBlock = statement.getFinallyBlock();
            if (finallyBlock == null) {
                return;
            }
            if (finallyBlock.getStatements().length != 0) {
                return;
            }
            final PsiElement[] children = statement.getChildren();
            for (final PsiElement child : children) {
                final String childText = child.getText();
                if (PsiKeyword.FINALLY.equals(childText)) {
                    registerError(child);
                    return;
                }
            }
        }
    }
}