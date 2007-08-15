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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SafeLockInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "LockAcquiredButNotSafelyReleased";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("safe.lock.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiExpression expression = (PsiExpression)infos[0];
        final PsiType type = expression.getType();
        assert type != null;
        final String text = type.getPresentableText();
        return InspectionGadgetsBundle.message(
                "safe.lock.problem.descriptor", text);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LockResourceVisitor();
    }

    private static class LockResourceVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isLockAcquireMethod(expression)) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression lhs = methodExpression.getQualifierExpression();
            if (!(lhs instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiElement referent =
                    ((PsiReference)lhs).resolve();
            if (referent == null || !(referent instanceof PsiVariable)) {
                return;
            }
            final PsiVariable boundVariable = (PsiVariable)referent;

            PsiElement currentContext = expression;
            while (true) {
                final PsiTryStatement tryStatement =
                        PsiTreeUtil.getParentOfType(currentContext,
                                PsiTryStatement.class);
                if (tryStatement == null) {
                    registerError(lhs, lhs);
                    return;
                }
                if (resourceIsOpenedInTryAndClosedInFinally(tryStatement,
                        expression,
                        boundVariable)) {
                    return;
                }
                currentContext = tryStatement;
            }
        }

        private static boolean resourceIsOpenedInTryAndClosedInFinally(
                PsiTryStatement tryStatement, PsiExpression lhs,
                PsiVariable boundVariable) {
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock == null) {
                return false;
            }
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return false;
            }
            if (!PsiTreeUtil.isAncestor(tryBlock, lhs, true)) {
                return false;
            }
            return containsResourceClose(finallyBlock, boundVariable);
        }

        private static boolean containsResourceClose(
                PsiCodeBlock finallyBlock, PsiVariable boundVariable) {
            final CloseVisitor visitor =
                    new CloseVisitor(boundVariable);
            finallyBlock.accept(visitor);
            return visitor.containsStreamClose();
        }

        private static boolean isLockAcquireMethod(
                PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!"lock".equals(methodName) &&
                    !"lockInterruptibly".equals(methodName)) {
                return false;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            return TypeUtils.expressionHasTypeOrSubtype(qualifier,
		            "java.util.concurrent.locks.Lock");
        }
    }

    private static class CloseVisitor extends PsiRecursiveElementVisitor {

        private boolean containsClose = false;
        private PsiVariable objectToClose;

        private CloseVisitor(PsiVariable objectToClose) {
            super();
            this.objectToClose = objectToClose;
        }

        public void visitElement(@NotNull PsiElement element) {
            if (!containsClose) {
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call) {
            if (containsClose) {
                return;
            }
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!"unlock".equals(methodName)) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiElement referent =
                    ((PsiReference)qualifier).resolve();
            if (referent == null) {
                return;
            }
            if (referent.equals(objectToClose)) {
                containsClose = true;
            }
        }

        public boolean containsStreamClose() {
            return containsClose;
        }
    }
}