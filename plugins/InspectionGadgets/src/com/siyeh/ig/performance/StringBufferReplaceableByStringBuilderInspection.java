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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class StringBufferReplaceableByStringBuilderInspection
        extends BaseInspection {

    @NotNull
    public String getID() {
        return "StringBufferMayBeStringBuilder";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "string.buffer.replaceable.by.string.builder.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "string.buffer.replaceable.by.string.builder.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new StringBufferMayBeStringBuilderFix();
    }

    private static class StringBufferMayBeStringBuilderFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "string.buffer.replaceable.by.string.builder.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement variableIdentifier =
                    descriptor.getPsiElement();
            final PsiLocalVariable variable =
                    (PsiLocalVariable)variableIdentifier.getParent();
            assert variable != null;
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement)variable.getParent();
            @NonNls final String text = declarationStatement.getText();
            final String newStatement = text.replaceAll("StringBuffer",
                    "StringBuilder");
            replaceStatement(declarationStatement, newStatement);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringBufferReplaceableByStringBuilderVisitor();
    }

    private static class StringBufferReplaceableByStringBuilderVisitor
            extends BaseInspectionVisitor {

        @Override public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(variable);
            if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
                return;
            }
            final PsiCodeBlock codeBlock =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (codeBlock == null) {
                return;
            }
            final PsiType type = variable.getType();
            if (!TypeUtils.typeEquals("java.lang.StringBuffer", type)) {
                return;
            }
            final PsiExpression initializer = variable.getInitializer();
            if (initializer == null) {
                return;
            }
            if (!isNewStringBuffer(initializer)) {
                return;
            }
            if (VariableAccessUtils.variableIsAssigned(variable, codeBlock)) {
                return;
            }
            if (VariableAccessUtils.variableIsAssignedFrom(variable,
                    codeBlock)) {
                return;
            }
            if (VariableAccessUtils.variableIsReturned(variable, codeBlock)) {
                return;
            }
            if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable,
                    codeBlock)) {
                return;
            }
            if (VariableAccessUtils.variableIsUsedInInnerClass(variable,
                    codeBlock)) {
                return;
            }
            registerVariableError(variable);
        }

        private static boolean isNewStringBuffer(PsiExpression expression) {
            if (expression == null) {
                return false;
            } else if (expression instanceof PsiNewExpression) {
                return true;
            } else if (expression instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression)expression;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                @NonNls final String methodName =
                        methodExpression.getReferenceName();
                if (!"append".equals(methodName)) {
                    return false;
                }
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                return isNewStringBuffer(qualifier);
            }
            return false;
        }
    }
}