/*
 * Copyright 2007 Bas Leijdekkers
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class SynchronizedOnLiteralObjectInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "synchronized.on.literal.object.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiType type = (PsiType)infos[0];
        return InspectionGadgetsBundle.message(
                "synchronized.on.literal.object.descriptor",
                type.getPresentableText());
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SynchronizeOnLockVisitor();
    }

    private static class SynchronizeOnLockVisitor
            extends BaseInspectionVisitor {

        public void visitSynchronizedStatement(
                @NotNull PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            final PsiExpression lockExpression = statement.getLockExpression();
            if (!(lockExpression instanceof PsiReferenceExpression)) {
                return;
            }
            if (!isNumberOrStringType(lockExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)lockExpression;
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return;
            }
            final PsiVariable variable = (PsiVariable)target;
            final PsiExpression initializer = variable.getInitializer();
            if (!(initializer instanceof PsiLiteralExpression)) {
                return;
            }
            registerError(lockExpression, lockExpression.getType());
        }

        public static boolean isNumberOrStringType(PsiExpression expression) {
            final PsiType type = expression.getType();
            if (type == null) {
                return false;
            }
            final PsiManager manager = expression.getManager();
            final Project project = manager.getProject();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiClass javaUtilLockClass =
                    manager.findClass("java.lang.Number", scope);
            if (javaUtilLockClass == null) {
                return false;
            }
            final PsiElementFactory elementFactory =
                    manager.getElementFactory();
            final PsiClassType javaLangNumberType =
                    elementFactory.createType(javaUtilLockClass);
            return type.equalsToText("java.lang.String") ||
                    type.equalsToText("java.lang.Boolean") ||
                    type.equalsToText("java.lang.Character") ||
                    javaLangNumberType.isAssignableFrom(type);
        }
    }
}