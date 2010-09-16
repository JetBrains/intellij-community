/*
 * Copyright 2006-2010 Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManualArrayToCollectionCopyInspection
        extends BaseInspection {

    @Override @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "manual.array.to.collection.copy.display.name");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "manual.array.to.collection.copy.problem.descriptor");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new ManualArrayToCollectionCopyFix();
    }

    private static class ManualArrayToCollectionCopyFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "manual.array.to.collection.copy.replace.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement forElement = descriptor.getPsiElement();
            final PsiElement parent = forElement.getParent();
            final String newExpression;
            if (parent instanceof PsiForStatement) {
                final PsiForStatement forStatement =
                        (PsiForStatement)parent;
                newExpression = getCollectionsAddAllText(forStatement);
                if (newExpression == null) {
                    return;
                }
                replaceStatementAndShortenClassNames(forStatement,
                        newExpression);

            } else {
                final PsiForeachStatement foreachStatement =
                        (PsiForeachStatement)parent;
                newExpression = getCollectionsAddAllText(foreachStatement);
                if (newExpression == null) {
                    return;
                }
                replaceStatementAndShortenClassNames(foreachStatement,
                        newExpression);
            }
        }

        @Nullable
        private static String getCollectionsAddAllText(
                PsiForeachStatement foreachStatement)
                throws IncorrectOperationException {
            final PsiStatement body = getBody(foreachStatement);
            if (!(body instanceof PsiExpressionStatement)) {
                return null;
            }
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement) body;
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)expressionStatement.getExpression();
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiElement collection = methodExpression.getQualifier();
            if (collection == null) {
                // fixme for when the array is added to 'this'
                return null;
            }
            final String collectionText = collection.getText();
            final PsiExpression iteratedValue =
                    foreachStatement.getIteratedValue();
            if (iteratedValue == null) {
                return null;
            }
            final String arrayText = iteratedValue.getText();
            @NonNls final StringBuilder buffer = new StringBuilder(60);
            buffer.append(collectionText);
            buffer.append(".addAll(java.util.Arrays.asList(");
            buffer.append(arrayText);
            buffer.append("));");
            return buffer.toString();
        }

        @Nullable
        private static String getCollectionsAddAllText(
                PsiForStatement forStatement)
                throws IncorrectOperationException {
            final PsiExpression expression = forStatement.getCondition();
            final PsiBinaryExpression condition =
                    (PsiBinaryExpression)ParenthesesUtils.stripParentheses(
                            expression);
            if (condition == null) {
                return null;
            }
            final PsiStatement initialization =
                    forStatement.getInitialization();
            if (initialization == null) {
                return null;
            }
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return null;
            }
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement)initialization;
            final PsiElement[] declaredElements =
                    declaration.getDeclaredElements();
            if (declaredElements.length != 1) {
                return null;
            }
            final PsiElement declaredElement = declaredElements[0];
            if (!(declaredElement instanceof PsiLocalVariable)) {
                return null;
            }
            final PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
            final String collectionText = buildCollectionText(forStatement);
            final PsiArrayAccessExpression arrayAccessExpression =
                    getArrayAccessExpression(forStatement);
            if (arrayAccessExpression == null) {
                return null;
            }
            final PsiExpression arrayExpression =
                    arrayAccessExpression.getArrayExpression();
            final String arrayText = arrayExpression.getText();
            final PsiExpression indexExpression =
                    arrayAccessExpression.getIndexExpression();
            final String fromOffsetText =
                    buildFromOffsetText(indexExpression, variable);
            if (fromOffsetText == null) {
                return null;
            }
            final PsiExpression limit;
            final IElementType tokenType = condition.getOperationTokenType();
            if (tokenType == JavaTokenType.LT ||
                    tokenType == JavaTokenType.LE)  {
                limit = condition.getROperand();
            } else {
                limit = condition.getLOperand();
            }
            @NonNls final String toOffsetText =
                    buildToOffsetText(limit, tokenType == JavaTokenType.LE ||
                            tokenType == JavaTokenType.GE);
            if (toOffsetText == null) {
                return null;
            }
            @NonNls final StringBuilder buffer = new StringBuilder();
            if (collectionText.length() > 0) {
                buffer.append(collectionText);
                buffer.append('.');
            }
            buffer.append("addAll(java.util.Arrays.asList(");
            buffer.append(arrayText);
            buffer.append(')');
            if (!fromOffsetText.equals("0") ||
                    !toOffsetText.equals(arrayText + ".length")) {
                buffer.append(".subList(");
                buffer.append(fromOffsetText);
                buffer.append(", ");
                buffer.append(toOffsetText);
                buffer.append(')');
            }
            buffer.append(");");
            return buffer.toString();
        }

        private static PsiArrayAccessExpression getArrayAccessExpression(
                PsiForStatement forStatement) {
            final PsiStatement body = getBody(forStatement);
            if (body == null) {
                return null;
            }
            final PsiExpression arrayAccessExpression;
            if (body instanceof PsiExpressionStatement) {
                final PsiExpressionStatement expressionStatement =
                        (PsiExpressionStatement) body;
                final PsiExpression expression =
                        expressionStatement.getExpression();
                if (!(expression instanceof PsiMethodCallExpression)) {
                    return null;
                }
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression)expression;
                final PsiExpressionList argumentList =
                        methodCallExpression.getArgumentList();
                arrayAccessExpression = argumentList.getExpressions()[0];
            } else if (body instanceof PsiDeclarationStatement) {
                final PsiDeclarationStatement declarationStatement =
                        (PsiDeclarationStatement) body;
                final PsiElement[] declaredElements =
                        declarationStatement.getDeclaredElements();
                if (declaredElements.length != 1) {
                    return null;
                }
                final PsiElement declaredElement = declaredElements[0];
                if (!(declaredElement instanceof PsiVariable)) {
                    return null;
                }
                final PsiVariable variable = (PsiVariable) declaredElement;
                arrayAccessExpression = variable.getInitializer();
            } else {
                return null;
            }
            final PsiExpression deparenthesizedArgument =
                    ParenthesesUtils.stripParentheses(arrayAccessExpression);
            if (!(deparenthesizedArgument instanceof
                    PsiArrayAccessExpression)) {
                return null;
            }
            return (PsiArrayAccessExpression) deparenthesizedArgument;
        }

        public static String buildCollectionText(PsiForStatement forStatement) {
            PsiStatement body = forStatement.getBody();
            while (body instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement) body;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length == 2) {
                    body = statements[1];
                } else if (statements.length == 1) {
                    body = statements[0];
                } else {
                    return null;
                }
            }
            if (!(body instanceof PsiExpressionStatement)) {
                return null;
            }
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement) body;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            if (!(expression instanceof PsiMethodCallExpression)) {
                return null;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) expression;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiElement qualifier = methodExpression.getQualifier();
            if (qualifier == null) {
                // fixme for when the array is added to 'this'
                return null;
            }
            return qualifier.getText();
        }

        @Nullable
        private static String buildFromOffsetText(PsiExpression expression,
                                                 PsiLocalVariable variable)
                throws IncorrectOperationException {
            expression = ParenthesesUtils.stripParentheses(expression);
            if (expression == null) {
                return null;
            }
            final String expressionText = expression.getText();
            final String variableName = variable.getName();
            if (expressionText.equals(variableName)) {
                final PsiExpression initialValue = variable.getInitializer();
                if (initialValue == null) {
                    return null;
                }
                return initialValue.getText();
            }
            if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)expression;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                final String rhsText = buildFromOffsetText(rhs, variable);
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                if (ExpressionUtils.isZero(lhs)) {
                    if (tokenType.equals(JavaTokenType.MINUS)) {
                        return '-' + rhsText;
                    }
                    return rhsText;
                }
                final String lhsText = buildFromOffsetText(lhs, variable);
                if (ExpressionUtils.isZero(rhs)) {
                    return lhsText;
                }
                return collapseConstant(lhsText + sign.getText() + rhsText,
                        variable);
            }
            return collapseConstant(expression.getText(), variable);
        }

        private static String buildToOffsetText(PsiExpression expression,
                                                boolean plusOne) {
            expression = ParenthesesUtils.stripParentheses(expression);
            if (expression == null) {
                return null;
            }
            if (!plusOne) {
                return expression.getText();
            }
            if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) expression;
                final IElementType tokenType =
                        binaryExpression.getOperationTokenType();
                if (tokenType == JavaTokenType.MINUS)  {
                    final PsiExpression rhs =
                            binaryExpression.getROperand();
                    if (ExpressionUtils.isOne(rhs)) {
                        return binaryExpression.getLOperand().getText();
                    }
                }
            }
            final int precedence = ParenthesesUtils.getPrecedence(expression);
            if (precedence > ParenthesesUtils.ADDITIVE_PRECEDENCE) {
                return '(' + expression.getText() + ")+1";
            } else {
                return expression.getText() + "+1";
            }
        }

        private static String collapseConstant(String expressionText,
                                               PsiElement context)
                throws IncorrectOperationException {
            final Project project = context.getProject();
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            final PsiElementFactory factory = psiFacade.getElementFactory();
            final PsiExpression fromOffsetExpression =
                    factory.createExpressionFromText(expressionText, context);
            final Object fromOffsetConstant =
                    ExpressionUtils.computeConstantExpression(
                            fromOffsetExpression);
            if (fromOffsetConstant != null) {
                return fromOffsetConstant.toString();
            } else {
                return expressionText;
            }
        }

        @Nullable
        private static PsiStatement getBody(PsiLoopStatement forStatement) {
            PsiStatement body = forStatement.getBody();
            while (body instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement)body;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                body = statements[0];
            }
            return body;
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ManualArrayToCollectionCopyVisitor();
    }

    private static class ManualArrayToCollectionCopyVisitor
            extends BaseInspectionVisitor {

        @Override public void visitForStatement(
                @NotNull PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiStatement initialization = statement.getInitialization();
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return;
            }
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement)initialization;
            final PsiElement[] declaredElements =
                    declaration.getDeclaredElements();
            if (declaredElements.length != 1) {
                return;
            }
            final PsiElement declaredElement = declaredElements[0];
            if (!(declaredElement instanceof PsiLocalVariable)) {
                return;
            }
            final PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
            final PsiExpression initialValue = variable.getInitializer();
            if (initialValue == null) {
                return;
            }
            final PsiExpression condition = statement.getCondition();
            if (!ExpressionUtils.isComparison(condition, variable)) {
                return;
            }
            final PsiStatement update = statement.getUpdate();
            if (!VariableAccessUtils.variableIsIncremented(variable, update)) {
                return;
            }
            final PsiStatement body = statement.getBody();
            if (!bodyIsArrayToCollectionCopy(body, variable, true)) {
                return;
            }
            registerStatementError(statement);
        }

        @Override public void visitForeachStatement(
                PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            final PsiExpression iteratedValue = statement.getIteratedValue();
            if (iteratedValue == null) {
                return;
            }
            final PsiType type = iteratedValue.getType();
            if (!(type instanceof PsiArrayType)) {
                return;
            }
            final PsiArrayType arrayType = (PsiArrayType)type;
            final PsiType componentType = arrayType.getComponentType();
            if (componentType instanceof PsiPrimitiveType) {
                return;
            }
            final PsiParameter parameter = statement.getIterationParameter();
            final PsiStatement body = statement.getBody();
            if (!bodyIsArrayToCollectionCopy(body, parameter, false)) {
                return;
            }
            registerStatementError(statement);
        }

        private static boolean bodyIsArrayToCollectionCopy(
                PsiStatement body, PsiVariable variable,
                boolean shouldBeOffsetArrayAccess) {
            if (body instanceof PsiExpressionStatement) {
                final PsiExpressionStatement expressionStatement =
                        (PsiExpressionStatement)body;
                final PsiExpression expression =
                        expressionStatement.getExpression();
                return expressionIsArrayToCollectionCopy(expression, variable,
                        shouldBeOffsetArrayAccess);
            } else if (body instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement)body;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length == 1) {
                    return bodyIsArrayToCollectionCopy(statements[0], variable,
                            shouldBeOffsetArrayAccess);
                } else if (statements.length == 2) {
                    final PsiStatement statement = statements[0];
                    if (!(statement instanceof PsiDeclarationStatement)) {
                        return false;
                    }
                    final PsiDeclarationStatement declarationStatement =
                            (PsiDeclarationStatement) statement;
                    final PsiElement[] declaredElements =
                            declarationStatement.getDeclaredElements();
                    if (declaredElements.length != 1) {
                        return false;
                    }
                    final PsiElement declaredElement = declaredElements[0];
                    if (!(declaredElement instanceof PsiVariable)) {
                        return false;
                    }
                    final PsiVariable localVariable =
                            (PsiVariable) declaredElement;
                    final PsiExpression initializer =
                            localVariable.getInitializer();
                    if (!ExpressionUtils.isOffsetArrayAccess(initializer,
                            variable)) {
                        return false;
                    }
                    return bodyIsArrayToCollectionCopy(statements[1],
                            localVariable, false);
                }
            }
            return false;
        }

        private static boolean expressionIsArrayToCollectionCopy(
                PsiExpression expression, PsiVariable variable,
                boolean shouldBeOffsetArrayAccess) {
            expression = ParenthesesUtils.stripParentheses(expression);
            if (expression == null) {
                return false;
            }
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)expression;
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return false;
            }
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression) &&
                    !(qualifier instanceof PsiThisExpression) &&
                    !(qualifier instanceof PsiSuperExpression)) {
                return false;
            }
            final PsiExpression argument = arguments[0];
            final PsiType argumentType = argument.getType();
            if (argumentType instanceof PsiPrimitiveType) {
                return false;
            }
            if (SideEffectChecker.mayHaveSideEffects(argument)) {
                return false;
            }
            if (shouldBeOffsetArrayAccess) {
                if (!ExpressionUtils.isOffsetArrayAccess(argument, variable)) {
                    return false;
                }
            } else if (!VariableAccessUtils.evaluatesToVariable(argument,
                    variable)) {
                return false;
            }
            final PsiMethod method = methodCallExpression.resolveMethod();
            if (method == null) {
                return false;
            }
            @NonNls final String name = method.getName();
            if (!name.equals("add")) {
                return false;
            }
            final PsiClass containingClass = method.getContainingClass();
            return ClassUtils.isSubclass(containingClass,
                    "java.util.Collection");
        }
    }
}