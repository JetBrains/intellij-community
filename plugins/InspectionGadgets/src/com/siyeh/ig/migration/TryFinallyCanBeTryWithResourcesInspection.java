/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.siyeh.ig.migration;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TryFinallyCanBeTryWithResourcesInspection extends BaseInspection {

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return "'try finally' replaceable with 'try' with resources ";
    }

    @NotNull
    @Override
    protected String buildErrorString(Object... infos) {
        return "<code>#ref</code> can use automatic resource management";
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TryFinallyCanBeTryWithResourcesVisitor();
    }

    private static class TryFinallyCanBeTryWithResourcesVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitTryStatement(PsiTryStatement tryStatement) {
            super.visitTryStatement(tryStatement);
            if (!PsiUtil.isLanguageLevel7OrHigher(tryStatement)) {
                return;
            }
            final PsiResourceList resourceList = tryStatement.getResourceList();
            if (resourceList != null) {
                return;
            }
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock == null) {
                return;
            }
            final PsiStatement[] statements = finallyBlock.getStatements();
            if (statements.length == 0) {
                return;
            }
            final Map<PsiVariable, PsiStatement> resourceVariables = new HashMap(2);
            for (PsiStatement statement : statements) {
                final PsiVariable variable =
                        findAutoCloseableVariable(statement);
                if (variable == null) {
                    continue;
                }
                resourceVariables.put(variable, null);
            }
            if (resourceVariables.isEmpty()) {
                return;
            }
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            final PsiStatement[] tryBlockStatements = tryBlock.getStatements();
            for (PsiVariable variable : resourceVariables.keySet()) {
                final PsiStatement initialization =
                        findInitialization(tryBlockStatements, variable);
                if (initialization != null) {
                    resourceVariables.put(variable, initialization);
                } else {
                    final PsiExpression initializer = variable.getInitializer();
                    if (initializer == null) {
                        return;
                    }
                    final PsiType type = initializer.getType();
                    if (PsiType.NULL.equals(type)) {
                        return;
                    }
                }
            }
            registerStatementError(tryStatement, resourceVariables);
        }

        private static PsiStatement findInitialization(
                PsiStatement[] statements, PsiVariable variable) {
            PsiStatement result = null;
            for (PsiStatement statement : statements) {
                if (!(statement instanceof PsiExpressionStatement)) {
                    continue;
                }
                final PsiExpressionStatement expressionStatement =
                        (PsiExpressionStatement) statement;
                final PsiExpression expression =
                        expressionStatement.getExpression();
                if (!(expression instanceof PsiAssignmentExpression)) {
                    continue;
                }
                final PsiAssignmentExpression assignmentExpression =
                        (PsiAssignmentExpression) expression;
                final PsiExpression lhs = assignmentExpression.getLExpression();
                if (!(lhs instanceof PsiReferenceExpression)) {
                    continue;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) lhs;
                final PsiElement target = referenceExpression.resolve();
                if (variable.equals(target)) {
                    if (result != null) {
                        return null;
                    }
                    result = statement;
                }
            }
            return result;
        }

        private static PsiVariable findAutoCloseableVariable(
                PsiStatement statement) {
            if (statement instanceof PsiIfStatement) {
                final PsiIfStatement ifStatement = (PsiIfStatement) statement;
                final PsiExpression condition = ifStatement.getCondition();
                if (!(condition instanceof PsiBinaryExpression)) {
                    return null;
                }
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) condition;
                final IElementType tokenType =
                        binaryExpression.getOperationTokenType();
                if (!JavaTokenType.NE.equals(tokenType)) {
                    return null;
                }
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                if (rhs == null) {
                    return null;
                }
                final PsiElement variable;
                if (PsiType.NULL.equals(rhs.getType())) {
                    if (!(lhs instanceof PsiReferenceExpression)) {
                        return null;
                    }
                    final PsiReferenceExpression referenceExpression =
                            (PsiReferenceExpression) lhs;
                    variable = referenceExpression.resolve();
                    if (!(variable instanceof PsiLocalVariable)) {
                        return null;
                    }
                } else if (PsiType.NULL.equals(lhs.getType())) {
                    if (!(rhs instanceof PsiReferenceExpression)) {
                        return null;
                    }
                    final PsiReferenceExpression referenceExpression =
                            (PsiReferenceExpression) rhs;
                    variable = referenceExpression.resolve();
                    if (!(variable instanceof PsiLocalVariable)) {
                        return null;
                    }
                } else {
                    return null;
                }
                final PsiStatement thenBranch = ifStatement.getThenBranch();
                final PsiVariable resourceVariable;
                if (thenBranch instanceof PsiExpressionStatement) {
                    resourceVariable = findAutoCloseableVariable(thenBranch);

                } else if (thenBranch instanceof PsiBlockStatement) {
                    final PsiBlockStatement blockStatement =
                            (PsiBlockStatement) thenBranch;
                    final PsiCodeBlock codeBlock =
                            blockStatement.getCodeBlock();
                    final PsiStatement[] statements = codeBlock.getStatements();
                    if (statements.length != 1) {
                        return null;
                    }
                    resourceVariable = findAutoCloseableVariable(statements[0]);
                } else {
                    return null;
                }
                if (variable.equals(resourceVariable)) {
                    return resourceVariable;
                }
            } else if (statement instanceof PsiExpressionStatement) {
                final PsiExpressionStatement expressionStatement =
                        (PsiExpressionStatement) statement;
                final PsiExpression expression =
                        expressionStatement.getExpression();
                if (!(expression instanceof PsiMethodCallExpression)) {
                    return null;
                }
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) expression;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                final String methodName = methodExpression.getReferenceName();
                if (!HardcodedMethodConstants.CLOSE.equals(methodName)) {
                    return null;
                }
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (!(qualifier instanceof PsiReferenceExpression)) {
                    return null;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) qualifier;
                final PsiElement target = referenceExpression.resolve();
                if (!(target instanceof PsiLocalVariable)) {
                    return null;
                }
                final PsiLocalVariable variable = (PsiLocalVariable) target;
                if (!isAutoCloseable(variable)) {
                    return null;
                }
                return variable;
            }
            return null;
        }

        private static boolean isAutoCloseable(PsiVariable variable) {
            final PsiType type = variable.getType();
            if (!(type instanceof PsiClassType)) {
                return false;
            }
            final PsiClassType classType = (PsiClassType) type;
            final PsiClass aClass = classType.resolve();
            return aClass != null && InheritanceUtil.isInheritor(aClass,
                    "java.io.Closeable"/*"java.lang.AutoCloseable"*/);
        }
    }
}
