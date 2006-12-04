/*
 * Copyright 2006 Bas Leijdekkers
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

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class MethodMayBeSynchronizedInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "method.may.be.synchronized.display.name");
    }

    @Nls @NotNull
    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "method.may.be.synchronized.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodMayBeSynchronizedVisitor();
    }

    private static class MethodMayBeSynchronizedVisitor
            extends BaseInspectionVisitor {

        public void visitSynchronizedStatement(
                PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            final PsiElement parent = statement.getParent();
            if (!(parent instanceof PsiMethod)) {
                return;
            }
            final PsiMethod method = (PsiMethod) parent;
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements.length != 1) {
                return;
            }
            final PsiExpression lockExpression = statement.getLockExpression();
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                if (!(lockExpression
                        instanceof PsiClassObjectAccessExpression)) {
                    return;
                }
                final PsiClassObjectAccessExpression classExpression =
                        (PsiClassObjectAccessExpression) lockExpression;
                final PsiTypeElement typeElement =
                        classExpression.getOperand();
                final PsiType type = typeElement.getType();
                if (!(type instanceof PsiClassType)) {
                    return;
                }
                final PsiClassType classType = (PsiClassType) type;
                final PsiClass aClass = classType.resolve();
                final PsiClass containingClass = method.getContainingClass();
                if (aClass != containingClass) {
                    return;
                }
                registerStatementError(statement);
            } else {
                if (!(lockExpression instanceof PsiThisExpression)) {
                    return;
                }
                registerStatementError(statement);
            }
        }
    }
}