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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TailRecursionInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("tail.recursion.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "tail.recursion.problem.descriptor");
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        final PsiMethod containingMethod =
                PsiTreeUtil.getParentOfType(location, PsiMethod.class);
        if (mayBeReplacedByIterativeMethod(containingMethod)) {
            return new RemoveTailRecursionFix();
        } else {
            return null;
        }
    }

    private static boolean mayBeReplacedByIterativeMethod(
            PsiMethod containingMethod) {
        if (!containingMethod.hasModifierProperty(PsiModifier.STATIC) &&
                !containingMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
            return false;
        }
        final PsiParameterList parameterList =
                containingMethod.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        for (final PsiParameter parameter : parameters) {
            if (parameter.hasModifierProperty(PsiModifier.FINAL)) {
                return false;
            }
        }
        return true;
    }

    private static class RemoveTailRecursionFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "tail.recursion.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement methodNameToken = descriptor.getPsiElement();
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(methodNameToken,
                            PsiMethod.class);
            if (method == null) {
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final PsiManager psiManager = PsiManager.getInstance(project);
            final CodeStyleManager codeStyleManager =
                    psiManager.getCodeStyleManager();
            final PsiElement[] children = body.getChildren();
            final boolean[] containedTailCallInLoop = new boolean[1];
            containedTailCallInLoop[0] = false;
            final StringBuffer buffer = new StringBuffer();
            for (int i = 1; i < children.length; i++) {
                replaceTailCalls(children[i], method, buffer,
                        containedTailCallInLoop);
            }
            final String labelString;
            if (containedTailCallInLoop[0]) {
                labelString = method.getName() + ':';
            } else {
                labelString = "";
            }
            @NonNls final String replacementText = '{' + labelString +
                    "while(true){" +
                    buffer + '}';

            final PsiElementFactory elementFactory =
                    psiManager.getElementFactory();
            final PsiCodeBlock block =
                    elementFactory.createCodeBlockFromText(replacementText,
                            null);
            body.replace(block);
            codeStyleManager.reformat(method);
        }

        private static void replaceTailCalls(
                PsiElement element, PsiMethod method,
                @NonNls StringBuffer out, boolean[] containedTailCallInLoop) {
            final String text = element.getText();
            if (isTailCallReturn(element, method)) {
                final PsiReturnStatement returnStatement =
                        (PsiReturnStatement)element;
                final PsiMethodCallExpression call = (PsiMethodCallExpression)
                        returnStatement.getReturnValue();
                assert call != null;
                final PsiExpressionList argumentList = call.getArgumentList();
                final PsiExpression[] args = argumentList.getExpressions();
                final PsiParameterList parameterList = method
                        .getParameterList();
                final PsiParameter[] parameters =
                        parameterList.getParameters();
                final boolean isInBlock =
                        returnStatement.getParent() instanceof PsiCodeBlock;
                if (!isInBlock) {
                    out.append('{');
                }
                for (int i = 0; i < parameters.length; i++) {
                    final PsiParameter parameter = parameters[i];
                    final PsiExpression arg = args[i];
                    final String parameterName = parameter.getName();
                    final String argText = arg.getText();
                    out.append(parameterName);
                    out.append(" = ");
                    out.append(argText);
                    out.append(';');
                }
                final PsiCodeBlock body = method.getBody();
                assert body != null;
                if (ControlFlowUtils.blockCompletesWithStatement(body,
                                returnStatement)) {
                    //don't do anything, as the continue is unnecessary
                } else if (ControlFlowUtils.isInLoop(element)) {
                    final String methodName = method.getName();
                    containedTailCallInLoop[0] = true;
                    out.append("continue ");
                    out.append(methodName);
                    out.append(';');
                } else {
                    out.append("continue;");
                }
                if (!isInBlock) {
                    out.append('}');
                }
            } else {
                final PsiElement[] children = element.getChildren();
                if (children.length == 0) {
                    out.append(text);
                } else {
                    for (final PsiElement child : children) {
                        replaceTailCalls(child, method, out,
                                containedTailCallInLoop);
                    }
                }
            }
        }

        private static boolean isTailCallReturn(PsiElement element,
                                                PsiMethod containingMethod) {
            if (!(element instanceof PsiReturnStatement)) {
                return false;
            }
            final PsiReturnStatement returnStatement =
                    (PsiReturnStatement)element;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            if (!(returnValue instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression)returnValue;
            final PsiMethod method = call.resolveMethod();
            return containingMethod.equals(method);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TailRecursionVisitor();
    }

    private static class TailRecursionVisitor extends BaseInspectionVisitor {

        public void visitReturnStatement(
                @NotNull PsiReturnStatement statement) {
            super.visitReturnStatement(statement);
            final PsiExpression returnValue = statement.getReturnValue();
            if (!(returnValue instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiMethod containingMethod =
                    PsiTreeUtil.getParentOfType(statement,
                            PsiMethod.class);
            if (containingMethod == null) {
                return;
            }
            final PsiMethodCallExpression returnCall =
                    (PsiMethodCallExpression)returnValue;
            final PsiMethod method = returnCall.resolveMethod();
            if (method == null) {
                return;
            }
            if (!method.equals(containingMethod)) {
                return;
            }
            registerMethodCallError(returnCall);
        }
    }
}