/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.resources;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.CheckBox;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class JNDIResourceInspection extends ResourceInspection {

    @SuppressWarnings({"PublicField"})
    public boolean insideTryAllowed = false;

    @Override
    @NotNull
    public String getID() {
        return "JNDIResourceOpenedButNotSafelyClosed";
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "jndi.resource.opened.not.closed.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiExpression expression = (PsiExpression) infos[0];
        final PsiType type = expression.getType();
        assert type != null;
        final String text = type.getPresentableText();
        return InspectionGadgetsBundle.message(
                "resource.opened.not.closed.problem.descriptor", text);
    }

    @Override
    public JComponent createOptionsPanel() {
        final JComponent panel = new JPanel(new GridBagLayout());
        final CheckBox checkBox = new CheckBox(
                InspectionGadgetsBundle.message(
                        "allow.resource.to.be.opened.inside.a.try.block"),
                this, "insideTryAllowed");

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets.left = 4;
        constraints.insets.right = 4;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(checkBox, constraints);
        return panel;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new JNDIResourceVisitor();
    }

    private class JNDIResourceVisitor extends BaseInspectionVisitor {

        @NonNls
        private static final String LIST = "list";
        @NonNls
        private static final String LIST_BINDING = "listBindings";
        @NonNls
        private static final String GET_ALL = "getAll";

        @Override
        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isJNDIFactoryMethod(expression)) {
                return;
            }
            final PsiElement parent = getExpressionParent(expression);
            if (parent instanceof PsiReturnStatement) {
                return;
            }
            final PsiVariable boundVariable = getVariable(parent);
            if (isSafelyClosed(boundVariable, expression, insideTryAllowed)) {
                return;
            }
            if (isResourceEscapedFromMethod(boundVariable, expression)) {
                return;
            }
            registerError(expression, expression);
        }


        @Override
        public void visitNewExpression(
                @NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if (!isJNDIResource(expression)) {
                return;
            }
            final PsiElement parent = getExpressionParent(expression);
            if (parent instanceof PsiReturnStatement) {
                return;
            }
            final PsiVariable boundVariable = getVariable(parent);
            if (isSafelyClosed(boundVariable, expression, insideTryAllowed)) {
                return;
            }
            if (isResourceEscapedFromMethod(boundVariable, expression)) {
                return;
            }
            registerError(expression, expression);
        }

        private boolean isJNDIResource(PsiNewExpression expression) {
            return TypeUtils.expressionHasTypeOrSubtype(expression,
                    "javax.naming.InitialContext");
        }

        private boolean isJNDIFactoryMethod(
                PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (LIST.equals(methodName) || LIST_BINDING.equals(methodName)) {
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (qualifier == null) {
                    return false;
                }
                return TypeUtils.expressionHasTypeOrSubtype(qualifier,
                        "javax.naming.Context");
            } else if (GET_ALL.equals(methodName)) {
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (qualifier == null) {
                    return false;
                }
                return TypeUtils.expressionHasTypeOrSubtype(qualifier,
                        "javax.naming.directory.Attribute") ||
                        TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                "javax.naming.directory.Attributes");
            } else {
                return false;
            }
        }
    }
}