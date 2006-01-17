/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.assignment;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class AssignmentToMethodParameterInspection
        extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "assignment.to.method.parameter.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ASSIGNMENT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message(
                "assignment.to.method.parameter.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new AssignmentToMethodParameterFix();
    }

    private static class AssignmentToMethodParameterFix
            extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "assignment.to.catch.block.parameter.extract.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiReferenceExpression parameterReference =
                    (PsiReferenceExpression)descriptor.getPsiElement();
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(parameterReference,
                            PsiMethod.class);
            if (method == null) {
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final PsiManager manager = PsiManager.getInstance(project);
            final CodeStyleManager codeStyleManager =
                    manager.getCodeStyleManager();
            final String parameterName = parameterReference.getText();
            final String variableName =
                    codeStyleManager.suggestUniqueVariableName(
                            parameterName, parameterReference, true);
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final PsiReference[] references =
                    searchHelper.findReferences(parameterReference.resolve(),
                            parameterReference.getUseScope(), false);
            if (references.length == 0 ||
                    !(references[0] instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression firstReference =
                    (PsiReferenceExpression)references[0];
            final PsiElement[] children = body.getChildren();
            final StringBuffer buffer = new StringBuffer();
            boolean newDeclarationCreated = false;
            for (int i = 1; i < children.length; i++) {
                newDeclarationCreated |=
                        replaceVariableName(children[i], firstReference,
                                variableName, parameterName, buffer);
            }
            final String replacementText;
            if (newDeclarationCreated) {
                replacementText = "{" + buffer;
            } else {
                final PsiType type = parameterReference.getType();
                if (type == null) {
                    return;
                }
                final String className = type.getPresentableText();
                replacementText = '{' + className + ' ' + variableName + " = " +
                        parameterName + ';' + buffer;
            }
            final PsiElementFactory elementFactory =
                    manager.getElementFactory();
            final PsiCodeBlock block =
                    elementFactory.createCodeBlockFromText(
                            replacementText, null);
            body.replace(block);
            codeStyleManager.reformat(method);
        }

        /**
         * @return true, if a declaration was introduced, false otherwise
         */
        private static boolean replaceVariableName(
                PsiElement element, PsiReferenceExpression firstReference,
                String newName, String originalName, StringBuffer out) {
            if (element instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression)element;
                if (isLeftSideOfSimpleAssignment(referenceExpression)) {
                    if (element.equals(firstReference)) {
                        final PsiType type = firstReference.getType();
                        if (type != null) {
                            out.append(type.getPresentableText());
                            out.append(' ');
                            out.append(newName);
                            return true;
                        }
                    }
                }
                final String text = element.getText();
                if (text.equals(originalName)) {
                    out.append(newName);
                    return false;
                }
            }
            final PsiElement[] children = element.getChildren();
            if (children.length == 0) {
                final String text = element.getText();
                out.append(text);
            } else {
                boolean result = false;
                for (final PsiElement child : children) {
                    if (result) {
                        out.append(child.getText());
                    } else {
                        result = replaceVariableName(child, firstReference,
                                newName, originalName, out);
                    }
                }
                return result;
            }
            return false;
        }

        private static boolean isLeftSideOfSimpleAssignment(
                PsiReferenceExpression reference) {
            if (reference == null) {
                return false;
            }
            final PsiElement parent = reference.getParent();
            if (!(parent instanceof PsiAssignmentExpression)) {
                return false;
            }
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression)parent;
            final PsiExpression lExpression =
                    assignmentExpression.getLExpression();
            if (!reference.equals(lExpression)) {
                return false;
            }
            final PsiExpression rExpression =
                    assignmentExpression.getRExpression();
            if (rExpression instanceof PsiAssignmentExpression) {
                return false;
            }
            final PsiElement grandParent = parent.getParent();
            return grandParent instanceof PsiExpressionStatement;
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssignmentToMethodParameterVisitor();
    }

    private static class AssignmentToMethodParameterVisitor
            extends BaseInspectionVisitor {

        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            final PsiExpression lhs = expression.getLExpression();
            checkForMethodParam(lhs);
        }

        public void visitPrefixExpression(
                @NotNull PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            checkForMethodParam(operand);
        }

        public void visitPostfixExpression(
                @NotNull PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            checkForMethodParam(operand);
        }

        private void checkForMethodParam(PsiExpression expression) {
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression ref =
                    (PsiReferenceExpression) expression;
            final PsiElement variable = ref.resolve();
            if (!(variable instanceof PsiParameter)) {
                return;
            }
            final PsiParameter parameter = (PsiParameter)variable;
            final PsiElement declarationScope = parameter.getDeclarationScope();
            if (declarationScope instanceof PsiCatchSection) {
                return;
            }
            if (declarationScope instanceof PsiForeachStatement) {
                return;
            }
            registerError(expression);
        }
    }
}