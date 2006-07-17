/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.StdLanguages;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class AutoBoxingInspection extends ExpressionInspection {

    /** @noinspection StaticCollection*/
    @NonNls static final Map<String,String> s_boxingClasses =
            new HashMap<String, String>(8);

    static {
        s_boxingClasses.put("byte", "java.lang.Byte");
        s_boxingClasses.put("short", "java.lang.Short");
        s_boxingClasses.put("int", "java.lang.Integer");
        s_boxingClasses.put("long", "java.lang.Long");
        s_boxingClasses.put("float", "java.lang.Float");
        s_boxingClasses.put("double", "java.lang.Double");
        s_boxingClasses.put("boolean", "java.lang.Boolean");
        s_boxingClasses.put("char", "java.lang.Character");
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("auto.boxing.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "auto.boxing.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AutoBoxingVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new AutoBoxingFix();
    }

    private static class AutoBoxingFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "auto.boxing.make.boxing.explicit.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression expression =
                    (PsiExpression) descriptor.getPsiElement();
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression, false);
            if (expectedType == null) {
                return;
            }
            final String expectedTypeText =
                    expectedType.getCanonicalText();
            final String classToConstruct;
            if (s_boxingClasses.containsValue(expectedTypeText)) {
                classToConstruct = expectedTypeText;
            } else {
                final PsiType type = expression.getType();
                if (type == null) {
                    return;
                }
                final String expressionTypeText = type.getCanonicalText();
                classToConstruct = s_boxingClasses.get(expressionTypeText);
            }
            @NonNls final String newExpression =
                    classToConstruct + ".valueOf(" + expression.getText() + ')';
            replaceExpression(expression, newExpression);
        }
    }

    private static class AutoBoxingVisitor extends BaseInspectionVisitor {

        public void visitElement(PsiElement element) {
            if (element.getLanguage() != StdLanguages.JAVA) {
                return;
            }
            final LanguageLevel languageLevel =
                    PsiUtil.getLanguageLevel(element);
            if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
                return;
            }
            super.visitElement(element);
        }

        public void visitArrayAccessExpression(
                PsiArrayAccessExpression expression) {
            super.visitArrayAccessExpression(expression);
            checkExpression(expression);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            checkExpression(expression);
        }

        public void visitConditionalExpression(
                PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            checkExpression(expression);
        }

        public void visitLiteralExpression(PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            checkExpression(expression);
        }

        public void visitPostfixExpression(PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            checkExpression(expression);
        }

        public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            checkExpression(expression);
        }

        public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            checkExpression(expression);
        }

        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            checkExpression(expression);
        }

        public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            checkExpression(expression);
        }

        public void visitAssignmentExpression(
                PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            checkExpression(expression);
        }

        public void visitParenthesizedExpression(
                PsiParenthesizedExpression expression) {
            super.visitParenthesizedExpression(expression);
            checkExpression(expression);
        }

        private void checkExpression(@NotNull PsiExpression expression) {
            if (expression.getParent() instanceof PsiParenthesizedExpression) {
                return;
            }
            final PsiType expressionType = expression.getType();
            if(expressionType == null) {
                return;
            }
            if(expressionType.equals(PsiType.VOID)) {
                return;
            }
            if(!TypeConversionUtil.isPrimitiveAndNotNull(expressionType)) {
                return;
            }
            final PsiPrimitiveType primitiveType =
                    (PsiPrimitiveType)expressionType;
            final PsiClassType boxedType =
                    primitiveType.getBoxedType(expression);
            if(boxedType == null){
                return;
            }
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression, false);
            if(expectedType == null) {
                return;
            }
            if(ClassUtils.isPrimitive(expectedType)) {
                return;
            }
            if(!expectedType.isAssignableFrom(boxedType)){
                return;
            }
            registerError(expression);
        }
    }
}