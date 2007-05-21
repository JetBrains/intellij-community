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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ExceptionInstanceNeverThrownInspection extends BaseInspection {
    
    @Nls @NotNull
    public String getDisplayName() {
        return "Exception instance never thrown";
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return "Exception instance <code>#ref</code> is never thrown";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExceptionInstanceNeverThrownVisitor();
    }

    private static class ExceptionInstanceNeverThrownVisitor
            extends BaseInspectionVisitor {

        public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiJavaCodeReferenceElement referenceElement =
                    expression.getClassOrAnonymousClassReference();
            if (referenceElement == null) {
                return;
            }
            final PsiElement element = referenceElement.resolve();
            if (!(element instanceof PsiClass)) {
                return;
            }
            final PsiClass aClass = (PsiClass) element;
            if (!ClassUtils.isSubclass(aClass, "java.lang.Throwable")) {
                return;
            }
            final PsiThrowStatement throwStatement =
                    PsiTreeUtil.getParentOfType(expression,
                            PsiThrowStatement.class);
            if (throwStatement != null) {
                return;
            }
            final PsiAssignmentExpression assignmentExpression =
                    PsiTreeUtil.getParentOfType(expression,
                            PsiAssignmentExpression.class);
            if (assignmentExpression == null) {
                return;
            }
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
            final Query<PsiReference> query = ReferencesSearch.search(target,
                    target.getUseScope());
            for (PsiReference reference : query) {
                final PsiElement usage = reference.getElement();
                if (PsiTreeUtil.getParentOfType(usage, PsiThrowStatement.class)
                        != null) {
                    return;
                }
            }
            registerError(expression);
        }
    }
}