/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
        return new SafeLockVisitor();
    }

    private static class SafeLockVisitor extends BaseInspectionVisitor {

        @Override public void visitMethodCallExpression(
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
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) lhs;
            final PsiElement referent = referenceExpression.resolve();
            if (referent == null || !(referent instanceof PsiVariable)) {
                return;
            }
            final PsiVariable boundVariable = (PsiVariable)referent;
            final PsiStatement statement =
                    PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
            if (statement == null) {
                return;
            }
            final PsiStatement nextStatement =
                    PsiTreeUtil.getNextSiblingOfType(statement,
                            PsiStatement.class);
            if (!(nextStatement instanceof PsiTryStatement)) {
                registerError(expression, referenceExpression);
                return;
            }
            PsiTryStatement tryStatement = (PsiTryStatement) nextStatement;
            if (lockIsUnlockedInFinally(tryStatement, boundVariable)) {
                return;
            }
            registerError(expression, referenceExpression);
        }

        private static boolean lockIsUnlockedInFinally(
                PsiTryStatement tryStatement, PsiVariable boundVariable) {
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock == null) {
                return false;
            }
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return false;
            }
            final UnlockVisitor visitor = new UnlockVisitor(boundVariable);
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

    private static class UnlockVisitor extends JavaRecursiveElementVisitor {

        private boolean containsClose = false;
        private PsiVariable objectToClose;

        private UnlockVisitor(PsiVariable objectToClose) {
            super();
            this.objectToClose = objectToClose;
        }

        @Override public void visitElement(@NotNull PsiElement element) {
            if (!containsClose) {
                super.visitElement(element);
            }
        }

        @Override public void visitMethodCallExpression(
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