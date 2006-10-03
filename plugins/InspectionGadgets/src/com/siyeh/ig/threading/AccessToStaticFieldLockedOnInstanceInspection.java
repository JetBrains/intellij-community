/*
 * Copyright 2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CollectionUtils;
import org.jetbrains.annotations.NotNull;

public class AccessToStaticFieldLockedOnInstanceInspection
        extends ExpressionInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "access.to.static.field.locked.on.instance.display.name");
    }

    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "access.to.static.field.locked.on.instance.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor
            extends BaseInspectionVisitor {

        public void visitReferenceExpression(
                @NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            boolean isLockedOnInstance = false;
            boolean isLockedOnClass = false;
            final PsiMethod containingMethod =
                    PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
            if (containingMethod != null &&
                    containingMethod.hasModifierProperty(
                            PsiModifier.SYNCHRONIZED)) {
                if (containingMethod.hasModifierProperty(
                        PsiModifier.STATIC)) {
                    isLockedOnClass = true;
                } else {
                    isLockedOnInstance = true;
                }
            }
            PsiElement elementToCheck = expression;
            while (true) {
                final PsiSynchronizedStatement syncStatement =
                        PsiTreeUtil.getParentOfType(elementToCheck,
                                PsiSynchronizedStatement.class);
                if (syncStatement == null) {
                    break;
                }
                final PsiExpression lockExpression =
                        syncStatement.getLockExpression();
                if (lockExpression instanceof PsiReferenceExpression) {
                    final PsiReferenceExpression reference =
                            (PsiReferenceExpression) lockExpression;
                    final PsiElement referent = reference.resolve();
                    if (referent instanceof PsiField) {
                        final PsiField referentField = (PsiField) referent;
                        if (referentField.hasModifierProperty(
                                PsiModifier.STATIC)) {
                            isLockedOnClass = true;
                        } else {
                            isLockedOnInstance = true;
                        }
                    }
                } else if (lockExpression instanceof PsiThisExpression) {
                    isLockedOnInstance = true;
                } else if (lockExpression instanceof
                        PsiClassObjectAccessExpression) {
                    isLockedOnClass = true;
                }
                elementToCheck = syncStatement;
            }
            if (!isLockedOnInstance || isLockedOnClass) {
                return;
            }
            final PsiElement referent = expression.resolve();
            if (!(referent instanceof PsiField)) {
                return;
            }
            final PsiField referredField = (PsiField) referent;
            if (!referredField.hasModifierProperty(PsiModifier.STATIC) ||
                    isConstant(referredField)) {
                return;
            }
            final PsiClass containingClass = referredField.getContainingClass();
            if (!PsiTreeUtil.isAncestor(containingClass, expression, false)) {
                return;
            }
            registerError(expression);
        }
    }

    private static boolean isConstant(PsiField field) {
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
        }
        if (CollectionUtils.isEmptyArray(field)) {
            return true;
        }
        final PsiType type = field.getType();
        return ClassUtils.isImmutable(type);
    }
}