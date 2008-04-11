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
package com.siyeh.ig.resources;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ResourceInspection extends BaseInspection {

    @Nullable
    protected static PsiVariable getVariable(
            @NotNull PsiElement element) {
        if (element instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignment =
                    (PsiAssignmentExpression) element;
            final PsiExpression lhs = assignment.getLExpression();
            if (!(lhs instanceof PsiReferenceExpression)) {
                return null;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) lhs;
            final PsiElement referent = referenceExpression.resolve();
            if (referent == null || !(referent instanceof PsiVariable)) {
                return null;
            }
            return (PsiVariable) referent;
        } else if (element instanceof PsiVariable) {
            return (PsiVariable) element;
        } else {
            return null;
        }
    }

    protected static PsiElement getExpressionParent(PsiExpression expression) {
        PsiElement parent = expression.getParent();
        while (parent instanceof PsiParenthesizedExpression ||
                parent instanceof PsiTypeCastExpression) {
            parent = parent.getParent();
        }
        return parent;
    }

    protected static boolean isSafelyClosed(@Nullable PsiVariable boundVariable,
                                            PsiExpression creationContext
    ) {
        if (boundVariable == null) {
            return false;
        }
        final PsiStatement statement =
                PsiTreeUtil.getParentOfType(creationContext, PsiStatement.class);
        if (statement == null) {
            return false;
        }
        final PsiStatement nextStatement =
                PsiTreeUtil.getNextSiblingOfType(statement,
                        PsiStatement.class);
        if (!(nextStatement instanceof PsiTryStatement)) {
            // exception in next statement can prevent closing of the resource
            return false;
        }
        final PsiTryStatement tryStatement = (PsiTryStatement) nextStatement;
        return resourceIsClosedInFinally(tryStatement, boundVariable);
    }

    protected static boolean resourceIsClosedInFinally(
            @NotNull PsiTryStatement tryStatement,
            @NotNull PsiVariable boundVariable) {
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock == null) {
            return false;
        }
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if (tryBlock == null) {
            return false;
        }
        final CloseVisitor visitor = new CloseVisitor(boundVariable);
        finallyBlock.accept(visitor);
        return visitor.containsClose();
    }

    protected static boolean isResourceEscapedFromMethod(
            PsiVariable boundVariable, PsiElement context){
        // poor man dataflow
        final PsiMethod method =
                PsiTreeUtil.getParentOfType(context, PsiMethod.class, true,
                        PsiMember.class);
        if(method == null){
            return false;
        }
        final PsiCodeBlock body = method.getBody();
        if(body == null){
            return false;
        }
        final EscapeVisitor visitor = new EscapeVisitor(boundVariable);
        body.accept(visitor);
        return visitor.isEscaped();
    }

    private static class CloseVisitor extends JavaRecursiveElementVisitor {

        private boolean containsClose = false;
        private PsiVariable objectToClose;

        private CloseVisitor(PsiVariable objectToClose) {
            this.objectToClose = objectToClose;
        }

        @Override
        public void visitElement(@NotNull PsiElement element) {
            if (!containsClose) {
                super.visitElement(element);
            }
        }

        @Override
        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call) {
            if (containsClose) {
                return;
            }
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.CLOSE.equals(methodName)) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReference reference = (PsiReference) qualifier;
            final PsiElement referent = reference.resolve();
            if (referent == null) {
                return;
            }
            if (referent.equals(objectToClose)) {
                containsClose = true;
            }
        }

        public boolean containsClose() {
            return containsClose;
        }
    }

    private static class EscapeVisitor extends JavaRecursiveElementVisitor{

        private final PsiVariable boundVariable;
        private boolean escaped = false;

        public EscapeVisitor(PsiVariable boundVariable){
            this.boundVariable = boundVariable;
        }

        @Override public void visitAnonymousClass(PsiAnonymousClass aClass){}

        @Override
        public void visitElement(PsiElement element){
            if(escaped){
                return;
            }
            super.visitElement(element);
        }

        @Override public void visitReturnStatement(
                PsiReturnStatement statement){
            PsiExpression value = statement.getReturnValue();
            value = PsiUtil.deparenthesizeExpression(value);
            if (value instanceof PsiReferenceExpression){
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) value;
                final PsiElement target = referenceExpression.resolve();
                if(target == boundVariable){
                    escaped = true;
                }
            }
        }

        public boolean isEscaped(){
            return escaped;
        }
    }
}
