/*
 * Copyright 2007-2008 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ThrowableInstanceNeverThrownInspection extends BaseInspection {
    
    @Nls @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "throwable.instance.never.thrown.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiExpression expression = (PsiExpression)infos[0];
        if (TypeUtils.expressionHasTypeOrSubtype(expression, "java.lang.RuntimeException")) {
            return InspectionGadgetsBundle.message(
                    "throwable.instance.never.thrown.runtime.exception.problem.descriptor");
        } else if (TypeUtils.expressionHasTypeOrSubtype(expression, "java.lang.Exception")) {
            return InspectionGadgetsBundle.message(
                    "throwable.instance.never.thrown.checked.exception.problem.descriptor");
        } else if (TypeUtils.expressionHasTypeOrSubtype(expression, "java.lang.Error")) {
            return InspectionGadgetsBundle.message(
                    "throwable.instance.never.thrown.error.problem.descriptor");
        } else {
            return InspectionGadgetsBundle.message(
                    "throwable.instance.never.thrown.problem.descriptor");
        }
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExceptionInstanceNeverThrownVisitor();
    }

    private static class ExceptionInstanceNeverThrownVisitor
            extends BaseInspectionVisitor {

        @Override public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if (!TypeUtils.expressionHasTypeOrSubtype(expression,
                    "java.lang.Throwable")) {
                return;
            }
            PsiElement parent = expression.getParent();
            while (parent instanceof PsiParenthesizedExpression) {
                parent = parent.getParent();
            }
            if (parent instanceof PsiThrowStatement) {
                return;
            } else if (parent instanceof PsiReturnStatement) {
                return;
            }
            final PsiElement typedParent =
                    PsiTreeUtil.getParentOfType(expression,
                            PsiAssignmentExpression.class,
                            PsiVariable.class);
            final PsiLocalVariable variable;
            if (typedParent instanceof PsiAssignmentExpression) {
                final PsiAssignmentExpression assignmentExpression =
                        (PsiAssignmentExpression)typedParent;
                final PsiExpression rhs = assignmentExpression.getRExpression();
                if (!PsiTreeUtil.isAncestor(rhs, expression, false)) {
                    return;
                }
                final PsiExpression lhs = assignmentExpression.getLExpression();
                if (!(lhs instanceof PsiReferenceExpression)) {
                    return;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) lhs;
                final PsiElement target = referenceExpression.resolve();
                if (!(target instanceof PsiLocalVariable)) {
                    return;
                }
                variable = (PsiLocalVariable)target;
            } else if (typedParent instanceof PsiVariable) {
                if (!(typedParent instanceof PsiLocalVariable)) {
                    return;
                }
                variable = (PsiLocalVariable)typedParent;
            } else {
                variable = null;
            }
            if (variable != null) {
                final Query<PsiReference> query =
                        ReferencesSearch.search(variable,
                                variable.getUseScope());
                for (PsiReference reference : query) {
                    final PsiElement usage = reference.getElement();
                    PsiElement usageParent = usage.getParent();
                    while (usageParent instanceof PsiParenthesizedExpression) {
                        usageParent = usageParent.getParent();
                    }
                    if (usageParent instanceof PsiThrowStatement) {
                        return;
                    } else if (usageParent instanceof PsiReturnStatement) {
                        return;
                    }
                }
            }
            registerError(expression, expression);
        }
    }
}