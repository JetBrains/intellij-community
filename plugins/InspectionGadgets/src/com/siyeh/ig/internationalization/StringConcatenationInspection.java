/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

public class StringConcatenationInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreAsserts = false;

    @SuppressWarnings({"PublicField"})
    public boolean ignoreSystemOuts = false;

    @SuppressWarnings({"PublicField"})
    public boolean ignoreSystemErrs = false;

    @SuppressWarnings({"PublicField"})
    public boolean ignoreThrowableArguments = false;

    @SuppressWarnings({"PublicField"})
    public boolean ignoreConstantInitializers = false;

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "string.concatenation.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "string.concatenation.problem.descriptor");
    }

    @Override
    @NotNull
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) infos[0];
        final PsiExpression lhs = binaryExpression.getLOperand();
        final Collection<InspectionGadgetsFix> result = new ArrayList();
        final PsiModifierListOwner element1 = getAnnotatableElement(lhs);
        if (element1 != null) {
            final InspectionGadgetsFix fix = new DelegatingFix(
                    new AddAnnotationFix(AnnotationUtil.NON_NLS, element1));
            result.add(fix);
        }
        final PsiExpression rhs = binaryExpression.getROperand();
        final PsiModifierListOwner element2 = getAnnotatableElement(rhs);
        if (element2 != null) {
            final InspectionGadgetsFix fix = new DelegatingFix(
                    new AddAnnotationFix(AnnotationUtil.NON_NLS, element2));
            result.add(fix);
        }
        final PsiElement expressionParent = PsiTreeUtil.getParentOfType(
                binaryExpression, PsiReturnStatement.class,
                PsiExpressionList.class);
        if (!(expressionParent instanceof PsiExpressionList) &&
                expressionParent != null) {
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(expressionParent,
                            PsiMethod.class);
            if (method != null) {
                final InspectionGadgetsFix fix = new DelegatingFix(
                        new AddAnnotationFix(AnnotationUtil.NON_NLS, method));
                result.add(fix);
            }
        }
        return result.toArray(new InspectionGadgetsFix[result.size()]);
    }

    @Nullable
    public static PsiModifierListOwner getAnnotatableElement(
            PsiExpression expression) {
        if (!(expression instanceof PsiReferenceExpression)) {
            return null;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression)expression;
        final PsiElement element = referenceExpression.resolve();
        if (!(element instanceof PsiModifierListOwner)) {
            return null;
        }
        return (PsiModifierListOwner)element;
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "string.concatenation.ignore.assert.option"),
                "ignoreAsserts");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "string.concatenation.ignore.system.out.option"),
                "ignoreSystemOuts");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "string.concatenation.ignore.system.err.option"),
                "ignoreSystemErrs");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "string.concatenation.ignore.exceptions.option"),
                "ignoreThrowableArguments");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "string.concatenation.ignore.constant.initializers.option"),
                "ignoreConstantInitializers");
        return optionsPanel;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StringConcatenationVisitor();
    }

    private class StringConcatenationVisitor
            extends BaseInspectionVisitor {

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiExpression rhs = expression.getROperand();
            if(rhs == null) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!JavaTokenType.PLUS.equals(tokenType)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiType lhsType = lhs.getType();
            final PsiType rhsType = rhs.getType();
            if(!TypeUtils.isJavaLangString(lhsType) &&
               !TypeUtils.isJavaLangString(rhsType)){
                return;
            }
            if (NonNlsUtils.isNonNlsAnnotated(lhs) ||
                    NonNlsUtils.isNonNlsAnnotated(rhs)) {
                return;
            }
            if (ignoreAsserts) {
                final PsiAssertStatement assertStatement =
                        PsiTreeUtil.getParentOfType(expression,
                                PsiAssertStatement.class, true,
                                PsiCodeBlock.class);
                if (assertStatement != null) {
                    return;
                }
            }
            if (ignoreSystemErrs || ignoreSystemOuts) {
                final PsiMethodCallExpression methodCallExpression =
                        PsiTreeUtil.getParentOfType(expression,
                                PsiMethodCallExpression.class, true,
                                PsiCodeBlock.class);
                if (methodCallExpression != null) {
                    final PsiReferenceExpression methodExpression =
                            methodCallExpression.getMethodExpression();
                    @NonNls
                    final String canonicalText =
                            methodExpression.getCanonicalText();
                    if (ignoreSystemOuts &&
                            "System.out.println".equals(canonicalText) ||
                            "System.out.print".equals(canonicalText)) {
                        return;
                    }
                    if (ignoreSystemErrs &&
                            "System.err.println".equals(canonicalText) ||
                            "System.err.print".equals(canonicalText)) {
                        return;
                    }
                }
            }
            if (ignoreThrowableArguments) {
                final PsiNewExpression newExpression =
                        PsiTreeUtil.getParentOfType(expression,
                                PsiNewExpression.class, true,
                                PsiCodeBlock.class);
                if (newExpression != null) {
                    final PsiType type = newExpression.getType();
                    if (type != null && InheritanceUtil.isInheritor(type,
                            "java.lang.Throwable")) {
                        return;
                    }
                }
            }
            if (ignoreConstantInitializers) {
                PsiElement parent = expression.getParent();
                while (parent instanceof PsiBinaryExpression) {
                    parent = parent.getParent();
                }
                if (parent instanceof PsiField) {
                    final PsiField field = (PsiField) parent;
                    if (field.hasModifierProperty(PsiModifier.STATIC) &&
                            field.hasModifierProperty(PsiModifier.FINAL)) {
                        return;
                    }
                    final PsiClass containingClass = field.getContainingClass();
                    if (containingClass != null &&
                            containingClass.isInterface()) {
                        return;
                    }
                }
            }
            if (NonNlsUtils.isNonNlsAnnotatedUse(expression)) {
                return;
            }
            registerError(sign, expression);
        }
    }
}
