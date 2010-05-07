/*
 * Copyright 2008-2010 Bas Leijdekkers
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

    protected static boolean isSafelyClosed(@Nullable PsiVariable variable,
                                            PsiElement context) {
        if (variable == null) {
            return false;
        }
        PsiStatement statement =
                PsiTreeUtil.getParentOfType(context, PsiStatement.class);
        if (statement == null) {
            return false;
        }
        PsiStatement nextStatement =
                PsiTreeUtil.getNextSiblingOfType(statement,
                        PsiStatement.class);
        while (nextStatement == null) {
            statement = PsiTreeUtil.getParentOfType(statement,
                    PsiStatement.class, true);
            if (statement == null) {
                return false;
            }
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiIfStatement) {
                statement = (PsiStatement) parent;
            }
            nextStatement =
                    PsiTreeUtil.getNextSiblingOfType(statement,
                            PsiStatement.class);
        }
        if (!(nextStatement instanceof PsiTryStatement)) {
            // exception in next statement can prevent closing of the resource
            return isResourceClose(nextStatement, variable);
        }
        final PsiTryStatement tryStatement = (PsiTryStatement) nextStatement;
        return resourceIsClosedInFinally(tryStatement, variable);
    }

    protected static boolean resourceIsClosedInFinally(
            @NotNull PsiTryStatement tryStatement,
            @NotNull PsiVariable variable) {
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock == null) {
            return false;
        }
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if (tryBlock == null) {
            return false;
        }
        final CloseVisitor visitor = new CloseVisitor(variable);
        finallyBlock.accept(visitor);
        return visitor.containsClose();
    }

    private static boolean isResourceClose(PsiStatement nextStatement,
                                           PsiVariable variable) {
        if (!(nextStatement instanceof PsiExpressionStatement)) {
            return false;
        }
        final PsiExpressionStatement expressionStatement =
                (PsiExpressionStatement) nextStatement;
        final PsiExpression expression = expressionStatement.getExpression();
        if (!(expression instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) expression;
        return isResourceClose(methodCallExpression, variable);
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

    protected static boolean isResourceClose(PsiMethodCallExpression call,
                                             PsiVariable resource) {
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if (!HardcodedMethodConstants.CLOSE.equals(methodName)) {
            return false;
        }
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiReference reference = (PsiReference) qualifier;
        final PsiElement referent = reference.resolve();
        return referent != null && referent.equals(resource);
    }

    private static class CloseVisitor extends JavaRecursiveElementVisitor {

        private boolean containsClose = false;
        private final PsiVariable resource;
        private final String resourceName;

        private CloseVisitor(PsiVariable resource) {
            this.resource = resource;
            this.resourceName = resource.getName();
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
            if (!isResourceClose(call, resource)) {
                return;
            }
            containsClose = true;
        }

        @Override
        public void visitReferenceExpression(
                PsiReferenceExpression referenceExpression) {
            // check if resource is closed in a method like IOUtils.silentClose()
            super.visitReferenceExpression(referenceExpression);
            if (containsClose) {
                return;
            }
            final String text = referenceExpression.getText();
            if (text == null || !text.equals(resourceName)) {
                return;
            }
            final PsiElement parent = referenceExpression.getParent();
            if (!(parent instanceof PsiExpressionList)) {
                return;
            }
            final PsiExpressionList argumentList = (PsiExpressionList) parent;
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) grandParent;
            final PsiElement target = referenceExpression.resolve();
            if (target == null || !target.equals(resource)) {
                return;
            }
            final PsiMethod method = methodCallExpression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiCodeBlock codeBlock = method.getBody();
            if (codeBlock == null) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length != 1) {
                return;
            }
            final PsiParameter parameter = parameters[0];
            final PsiStatement[] statements = codeBlock.getStatements();
            for (PsiStatement statement : statements) {
                if (!(statement instanceof PsiTryStatement)) {
                    continue;
                }
                final PsiTryStatement tryStatement = (PsiTryStatement) statement;
                final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
                if (tryBlock == null) {
                    return;
                }
                final PsiStatement[] innerStatements = tryBlock.getStatements();
                for (PsiStatement innerStatement : innerStatements) {
                    if (!(innerStatement instanceof PsiExpressionStatement)) {
                        continue;
                    }
                    final PsiExpressionStatement expressionStatement =
                            (PsiExpressionStatement) innerStatement;
                    final PsiExpression expression =
                            expressionStatement.getExpression();
                    if (!(expression instanceof PsiMethodCallExpression)) {
                        continue;
                    }
                    final PsiMethodCallExpression potentialCloseExpression =
                            (PsiMethodCallExpression) expression;
                    if (isResourceClose(potentialCloseExpression, parameter)) {
                        containsClose = true;
                        return;
                    }
                }
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
