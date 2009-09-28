/*
 * Copyright 2008 Bas Leijdekkers
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
package com.siyeh.ig.logging;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Document;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;


public class LoggerInitializedWithForeignClassInspection
        extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public String loggerClassName = "org.apache.log4j.Logger";

    @SuppressWarnings({"PublicField"})
    public String loggerFactoryMethodName = "getLogger";

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "logger.initialized.with.foreign.class.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "logger.initialized.with.foreign.class.problem.descriptor");
    }

    @Override
    public JComponent createOptionsPanel() {
        return new Form().getContentPanel();
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new LoggerInitializedWithForeignClassFix((String)infos[0]);
    }

    private static class LoggerInitializedWithForeignClassFix
            extends InspectionGadgetsFix {

        private final String newClassName;

        private LoggerInitializedWithForeignClassFix(String newClassName) {
            this.newClassName = newClassName;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "logger.initialized.with.foreign.class.quickfix",
                    newClassName);
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiClassObjectAccessExpression)) {
                return;
            }
            final PsiClassObjectAccessExpression classObjectAccessExpression =
                    (PsiClassObjectAccessExpression) element;
            replaceExpression(classObjectAccessExpression,
                    newClassName + ".class");
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LoggerInitializedWithForeignClassVisitor();
    }

    private class LoggerInitializedWithForeignClassVisitor
            extends BaseInspectionVisitor {

        public void visitClassObjectAccessExpression(
                PsiClassObjectAccessExpression expression) {
            super.visitClassObjectAccessExpression(expression);
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiExpressionList)) {
                return;
            }
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) grandParent;
            final PsiClass containingClass = PsiTreeUtil.getParentOfType(
                    expression, PsiClass.class);
            if (containingClass == null) {
                return;
            }
            final String containingClassName = containingClass.getName();
            if (containingClassName == null) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final String referenceName = methodExpression.getReferenceName();
            if (!loggerFactoryMethodName.equals(referenceName)) {
                return;
            }
            final PsiMethod method = methodCallExpression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            final String className = aClass.getQualifiedName();
            if (!loggerClassName.equals(className)) {
                return;
            }
            final PsiTypeElement operand = expression.getOperand();
            final PsiType type = operand.getType();
            if (!(type instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType) type;
            final PsiClass initializerClass = classType.resolve();
            if (initializerClass == null) {
                return;
            }
            if (containingClass.equals(initializerClass)) {
                return;
            }
            registerError(expression, containingClassName);
        }
    }

    class Form {

        private JPanel contentPanel;
        private JTextField loggerClassNameTextField;
        private JTextField loggerFactoryMethodNameTextField;

        Form() {
            loggerClassNameTextField.setText(loggerClassName);
            final DocumentListener listener = new DocumentListener() {

                public void changedUpdate(DocumentEvent e) {
                    textChanged();
                }

                public void insertUpdate(DocumentEvent e) {
                    textChanged();
                }

                public void removeUpdate(DocumentEvent e) {
                    textChanged();
                }

                private void textChanged() {
                    loggerClassName =  loggerClassNameTextField.getText();
                }
            };
            final Document document = loggerClassNameTextField.getDocument();
            document.addDocumentListener(listener);
            loggerFactoryMethodNameTextField.setText(loggerFactoryMethodName);
            final DocumentListener factoryListener = new DocumentListener() {

                public void changedUpdate(DocumentEvent e) {
                    textChanged();
                }

                public void insertUpdate(DocumentEvent e) {
                    textChanged();
                }

                public void removeUpdate(DocumentEvent e) {
                    textChanged();
                }

                private void textChanged() {
                    loggerClassName =  loggerClassNameTextField.getText();
                }
            };
            final Document factoryDocument =
                    loggerFactoryMethodNameTextField.getDocument();
            factoryDocument.addDocumentListener(factoryListener);
        }

        public JComponent getContentPanel() {
            return contentPanel;
        }
    }
}
