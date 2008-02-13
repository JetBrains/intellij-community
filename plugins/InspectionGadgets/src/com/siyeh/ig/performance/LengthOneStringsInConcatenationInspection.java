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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LengthOneStringsInConcatenationInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "length.one.strings.in.concatenation.display.name");
    }

    @NotNull
    public String getID() {
        return "SingleCharacterStringConcatenation";
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final String string = (String)infos[0];
        final String escapedString = StringUtil.escapeStringCharacters(string);
        return InspectionGadgetsBundle.message(
                "length.one.strings.in.concatenation.problem.descriptor",
                escapedString);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LengthOneStringsInConcatenationVisitor();
    }

    public InspectionGadgetsFix buildFix(Object... infos) {
        return new ReplaceStringsWithCharsFix();
    }

    private static class ReplaceStringsWithCharsFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "length.one.strings.in.concatenation.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression expression =
                    (PsiExpression)descriptor.getPsiElement();
            final String text = expression.getText();
            final int length = text.length();
            final String character = text.substring(1, length - 1);
            final String charLiteral;
            if ("\'".equals(character)) {
                charLiteral = "'\\''";
            } else {
                charLiteral = '\'' + character + '\'';
            }
            replaceExpression(expression, charLiteral);
        }
    }

    private static class LengthOneStringsInConcatenationVisitor
            extends BaseInspectionVisitor {

        @Override public void visitLiteralExpression(
                @NotNull PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            final PsiType type = expression.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            final String value = (String)expression.getValue();
            if (value == null || value.length() != 1) {
                return;
            }
            if (!isArgumentOfConcatenation(expression) &&
                !isArgumentOfStringAppend(expression)) {
                return;
            }
            registerError(expression, value);
        }

        private static boolean isArgumentOfConcatenation(
                PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiBinaryExpression)) {
                return false;
            }
            final PsiBinaryExpression binaryExp = (PsiBinaryExpression)parent;
            final PsiJavaToken sign = binaryExp.getOperationSign();
            if (!JavaTokenType.PLUS.equals(sign.getTokenType())) {
                return false;
            }
            final PsiExpression lhs = binaryExp.getLOperand();
            final PsiExpression sibling;
            if (lhs.equals(expression)) {
                sibling = binaryExp.getROperand();
            } else {
                sibling = lhs;
            }
            if (sibling == null) {
                return false;
            }
            final PsiType siblingType = sibling.getType();
            return TypeUtils.isJavaLangString(siblingType);
        }

        static boolean isArgumentOfStringAppend(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (parent == null) {
                return false;
            }
            if (!(parent instanceof PsiExpressionList)) {
                return false;
            }
            final PsiElement grandparent = parent.getParent();
            if (!(grandparent instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression)grandparent;
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            @NonNls final String name = methodExpression.getReferenceName();
            if (!"append".equals(name) && !"insert".equals(name)) {
                return false;
            }
            final PsiMethod method = call.resolveMethod();
            if (method == null) {
                return false;
            }
            final PsiClass methodClass = method.getContainingClass();
            if (methodClass == null) {
                return false;
            }
            final String className = methodClass.getQualifiedName();
            return "java.lang.StringBuffer".equals(className) ||
                   "java.lang.StringBuilder".equals(className);
        }
    }
}