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
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.CheckBox;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class JDBCResourceInspection extends ResourceInspection {

    private static final String[] creationMethodClassName =
            {
                    "java.sql.Driver",
                    "java.sql.DriverManager",
                    "javax.sql.DataSource",
                    "java.sql.Connection",
                    "java.sql.Connection",
                    "java.sql.Connection",
                    "java.sql.Statement",
                    "java.sql.Statement",
                    "java.sql.Statement",
            };
    @NonNls
    private static final String[] creationMethodName =
            {
                    "connect",
                    "getConnection",
                    "getConnection",
                    "createStatement",
                    "prepareStatement",
                    "prepareCall",
                    "executeQuery",
                    "getResultSet",
                    "getGeneratedKeys"
            };

    @SuppressWarnings({"StaticCollection"})
    private static final Set<String> creationMethodNameSet =
            new HashSet<String>(9);

    @SuppressWarnings({"PublicField"})
    public boolean insideTryAllowed = false;

    static {
        ContainerUtil.addAll(creationMethodNameSet, creationMethodName);
    }

    @Override
    @NotNull
    public String getID() {
        return "JDBCResourceOpenedButNotSafelyClosed";
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "jdbc.resource.opened.not.closed.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiExpression expression = (PsiExpression) infos[0];
        final PsiType type = expression.getType();
        assert type != null;
        final String text = type.getPresentableText();
        return InspectionGadgetsBundle.message(
                "jdbc.resource.opened.not.closed.problem.descriptor", text);
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
        return new JDBCResourceVisitor();
    }

    private class JDBCResourceVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isJDBCResourceCreation(expression)) {
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

        private boolean isJDBCResourceCreation(
                PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String name = methodExpression.getReferenceName();
            if (name == null) {
                return false;
            }
            if (!creationMethodNameSet.contains(name)) {
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            for (int i = 0; i < creationMethodName.length; i++) {
                if (!name.equals(creationMethodName[i])) {
                    continue;
                }
                final PsiClass containingClass = method.getContainingClass();
                if (containingClass == null) {
                    return false;
                }
                final String className = containingClass.getQualifiedName();
                if (className == null) {
                    return false;
                }
                if (className.equals(creationMethodClassName[i])) {
                    return true;
                }
            }
            return false;
        }
    }
}
