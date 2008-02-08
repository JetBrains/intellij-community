/*
 * Copyright 2008 Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class SynchronizationOnLocalVariableOrMethodParameterInspection
        extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean reportLocalVariables = true;
    @SuppressWarnings({"PublicField"})
    public boolean reportMethodParameters = true;

    @Nls @NotNull
    public String getDisplayName() {
        return "Synchronization on local variable";
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return "Synchronization on local variable <code>#ref</code>";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SynchronizationOnLocalVariableVisitor();
    }

    private class SynchronizationOnLocalVariableVisitor
            extends BaseInspectionVisitor {

        public void visitSynchronizedStatement(
                PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            if (!reportLocalVariables && !reportMethodParameters) {
                return;
            }
            final PsiExpression lockExpression = statement.getLockExpression();
            if (!(lockExpression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) lockExpression;
            if (referenceExpression.isQualified()) {
                return;
            }
            final PsiElement target = referenceExpression.resolve();
            if (target instanceof PsiLocalVariable) {
                if (!reportLocalVariables) {
                    return;
                }
            } else if (target instanceof PsiParameter) {
                final PsiParameter parameter = (PsiParameter) target;
                final PsiElement scope = parameter.getDeclarationScope();
                if (scope instanceof PsiMethod) {
                    if (!reportMethodParameters) {
                        return;
                    }
                } else {
                    if (!reportLocalVariables) {
                        return;
                    }
                }
            } else {
                return;
            }
            final PsiClass parentClass =
                    PsiTreeUtil.getParentOfType(statement, PsiClass.class);
            if (!PsiTreeUtil.isAncestor(parentClass, target, true)) {
                // different class, probably different thread.
                return;
            }
            registerStatementError(statement, target);
        }
    }
}