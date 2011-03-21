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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TryFinallyCanBeTryWithResourcesInspection extends BaseInspection {

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "try.finally.can.be.try.with.resources.display.name");
    }

    @NotNull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "try.finally.can.be.try.with.resources.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new TryFinallyCanBeTryWithResourcesFix();
    }

    private static class TryFinallyCanBeTryWithResourcesFix
            extends InspectionGadgetsFix {

        public TryFinallyCanBeTryWithResourcesFix() {}

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "try.finally.can.be.try.with.resources.quickfix");
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiTryStatement)) {
                return;
            }
            final PsiTryStatement tryStatement = (PsiTryStatement) parent;
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            final PsiStatement[] tryBlockStatements = tryBlock.getStatements();
            final Set<PsiLocalVariable> variables =
                    collectVariables(tryStatement);
            final PsiElementFactory factory =
                    JavaPsiFacade.getElementFactory(project);
            @NonNls final StringBuilder newTryStatementText =
                    new StringBuilder("try (");
            final Set<Integer> skipStatements = new HashSet(2);
            boolean moreThanOne = false;
            for (PsiLocalVariable variable : variables) {
                final boolean hasInitializer;
                final PsiExpression initializer = variable.getInitializer();
                if (initializer == null) {
                    hasInitializer = false;
                } else {
                    final PsiType type = initializer.getType();
                    hasInitializer = !PsiType.NULL.equals(type);
                }
                if (moreThanOne) {
                    newTryStatementText.append(';');
                }
                newTryStatementText.append(variable.getTypeElement().getText());
                newTryStatementText.append(' ');
                newTryStatementText.append(variable.getName());
                newTryStatementText.append('=');
                final int index;
                if (hasInitializer) {
                    newTryStatementText.append(initializer.getText());
                } else {
                    index = findInitialization(tryBlockStatements,
                            variable, hasInitializer);
                    if (index < 0) {
                        return;
                    }
                    skipStatements.add(Integer.valueOf(index));
                    final PsiExpressionStatement expressionStatement =
                            (PsiExpressionStatement)
                                    tryBlockStatements[index];
                    final PsiAssignmentExpression assignmentExpression =
                            (PsiAssignmentExpression)
                                    expressionStatement.getExpression();
                    final PsiExpression rhs =
                            assignmentExpression.getRExpression();
                    if (rhs == null) {
                        return;
                    }
                    newTryStatementText.append(rhs.getText());
                }
                moreThanOne = true;
            }
            newTryStatementText.append(") {\n");
            final int tryBlockStatementsLength = tryBlockStatements.length;
            for (int i = 0; i < tryBlockStatementsLength; i++) {
                if (skipStatements.contains(Integer.valueOf(i))) {
                    continue;
                }
                final PsiStatement statement = tryBlockStatements[i];
                newTryStatementText.append(statement.getText());
                newTryStatementText.append('\n');
            }
            newTryStatementText.append('}');
            final PsiCatchSection[] catchSections =
                    tryStatement.getCatchSections();
            for (PsiCatchSection catchSection : catchSections) {
                newTryStatementText.append(catchSection.getText());
            }
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            assert finallyBlock != null;
            final PsiStatement[] statements = finallyBlock.getStatements();
            boolean appended = false;
            for (PsiStatement statement : statements) {
                if (isCloseStatement(statement, variables)) {
                    continue;
                }
                if (!appended) {
                    newTryStatementText.append(" finally {\n");
                    appended = true;
                }
                newTryStatementText.append(statement.getText());
                newTryStatementText.append('\n');
            }
            if (appended) {
                newTryStatementText.append('}');
            }
            for (PsiLocalVariable variable : variables) {
                variable.delete();
            }
            final PsiStatement newTryStatement =
                    factory.createStatementFromText(
                            newTryStatementText.toString(), element);
            tryStatement.replace(newTryStatement);
        }

        private boolean isCloseStatement(PsiStatement statement,
                                         Set<PsiLocalVariable> variables) {
            if (statement instanceof PsiExpressionStatement) {
                final PsiExpressionStatement expressionStatement =
                        (PsiExpressionStatement) statement;
                final PsiExpression expression =
                        expressionStatement.getExpression();
                if (!(expression instanceof PsiMethodCallExpression)) {
                    return false;
                }
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) expression;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                final String methodName = methodExpression.getReferenceName();
                if (!HardcodedMethodConstants.CLOSE.equals(methodName)) {
                    return false;
                }
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (!(qualifier instanceof PsiReferenceExpression)) {
                    return false;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) qualifier;
                final PsiElement target = referenceExpression.resolve();
                if (!(target instanceof PsiLocalVariable)) {
                    return false;
                }
                final PsiLocalVariable variable = (PsiLocalVariable) target;
                return variables.contains(variable);
            } else if (statement instanceof PsiIfStatement) {
                final PsiIfStatement ifStatement = (PsiIfStatement) statement;
                if (ifStatement.getElseBranch() != null) {
                    return false;
                }
                final PsiExpression condition = ifStatement.getCondition();
                if (!(condition instanceof PsiBinaryExpression)) {
                    return false;
                }
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) condition;
                final IElementType tokenType =
                        binaryExpression.getOperationTokenType();
                if (!JavaTokenType.NE.equals(tokenType)) {
                    return false;
                }
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                if (rhs == null) {
                    return false;
                }
                final PsiElement variable;
                if (PsiType.NULL.equals(rhs.getType())) {
                    if (!(lhs instanceof PsiReferenceExpression)) {
                        return false;
                    }
                    final PsiReferenceExpression referenceExpression =
                            (PsiReferenceExpression) lhs;
                    variable = referenceExpression.resolve();
                    if (!(variable instanceof PsiLocalVariable)) {
                        return false;
                    }
                } else if (PsiType.NULL.equals(lhs.getType())) {
                    if (!(rhs instanceof PsiReferenceExpression)) {
                        return false;
                    }
                    final PsiReferenceExpression referenceExpression =
                            (PsiReferenceExpression) rhs;
                    variable = referenceExpression.resolve();
                    if (!(variable instanceof PsiLocalVariable)) {
                        return false;
                    }
                } else {
                    return false;
                }
                final PsiStatement thenBranch = ifStatement.getThenBranch();
                if (thenBranch instanceof PsiExpressionStatement) {
                    return isCloseStatement(thenBranch, variables);
                } else if (thenBranch instanceof PsiBlockStatement) {
                    final PsiBlockStatement blockStatement =
                            (PsiBlockStatement) thenBranch;
                    final PsiCodeBlock codeBlock =
                            blockStatement.getCodeBlock();
                    final PsiStatement[] statements = codeBlock.getStatements();
                    return statements.length == 1 &&
                            isCloseStatement(statements[0], variables);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
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
            final Set<PsiLocalVariable> variables =
                    collectVariables(tryStatement);
            if (variables.isEmpty()) {
                return;
            }
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            final PsiStatement[] tryBlockStatements = tryBlock.getStatements();
            boolean found = false;
            for (PsiVariable variable : variables) {
                final boolean hasInitializer;
                final PsiExpression initializer = variable.getInitializer();
                if (initializer == null) {
                    hasInitializer = false;
                } else {
                    final PsiType type = initializer.getType();
                    hasInitializer = !PsiType.NULL.equals(type);
                }
                final int index = findInitialization(tryBlockStatements,
                        variable, hasInitializer);
                if (index >= 0 ^ hasInitializer) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return;
            }
            registerStatementError(tryStatement);
        }
    }

    static Set<PsiLocalVariable> collectVariables(
            PsiTryStatement tryStatement) {
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock == null) {
            return Collections.EMPTY_SET;
        }
        final PsiStatement[] statements = finallyBlock.getStatements();
        if (statements.length == 0) {
            return Collections.EMPTY_SET;
        }
        final Set<PsiLocalVariable> variables = new LinkedHashSet();
        for (PsiStatement statement : statements) {
            final PsiLocalVariable variable =
                    findAutoCloseableVariable(statement);
            if (variable != null) {
                variables.add(variable);
            }
        }
        return variables;
    }

    static PsiLocalVariable findAutoCloseableVariable(
            PsiStatement statement) {
        if (statement instanceof PsiIfStatement) {
            final PsiIfStatement ifStatement = (PsiIfStatement) statement;
            if (ifStatement.getElseBranch() != null) {
                return null;
            }
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
            final PsiLocalVariable resourceVariable;
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
            if (target instanceof PsiResourceVariable) {
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
                "java.lang.AutoCloseable");
    }

    static int findInitialization(
            PsiStatement[] statements, PsiVariable variable,
            boolean hasInitializer) {
        int result = -1;
        final int statementsLength = statements.length;
        for (int i = 0; i < statementsLength; i++) {
            final PsiStatement statement = statements[i];
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
                if (result >= 0 && !hasInitializer) {
                    return -1;
                }
                result = i;
            }
        }
        return result;
    }
}
