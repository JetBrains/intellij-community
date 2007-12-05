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
package com.siyeh.ig.jdk;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class AutoBoxingInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreAddedToCollection = false;

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

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("auto.boxing.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "auto.boxing.problem.descriptor");
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "auto.boxing.ignore.added.to.collection.option"), this,
                "ignoreAddedToCollection");
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

    private class AutoBoxingVisitor extends BaseInspectionVisitor {

        @Override public void visitElement(PsiElement element) {
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

        @Override public void visitArrayAccessExpression(
                PsiArrayAccessExpression expression) {
            super.visitArrayAccessExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitConditionalExpression(
                PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitPostfixExpression(PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitAssignmentExpression(
                PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitParenthesizedExpression(
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
            if (ignoreAddedToCollection && isAddedToCollection(expression)) {
                return;
            }
            registerError(expression);
        }

        private boolean isAddedToCollection(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiExpressionList)) {
                return false;
            }
            final PsiExpressionList expressionList = (PsiExpressionList)parent;
            final PsiElement grandParent = expressionList.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)grandParent;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!"put".equals(methodName) && !"set".equals(methodName) &&
                    !"add".equals(methodName)) {
                return false;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            return TypeUtils.expressionHasTypeOrSubtype(qualifier,
                    "java.util.Collection", "java.util.Map");
        }
    }
}