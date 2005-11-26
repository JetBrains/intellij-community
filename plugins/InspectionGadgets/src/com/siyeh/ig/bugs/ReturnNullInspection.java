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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ButtonModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

public class ReturnNullInspection extends StatementInspection {

    /** @noinspection PublicField*/
    public boolean m_reportObjectMethods = true;
    /** @noinspection PublicField*/
    public boolean m_reportArrayMethods = true;

    public String getID() {
        return "ReturnOfNull";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("return.of.null.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        if (AnnotationUtil.isAnnotatingApplicable(location)) {
            return new MakeNullableFix();
        } else {
            return null;
        }
    }

    private static class MakeNullableFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message("return.of.null.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement nullToken = descriptor.getPsiElement();
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(nullToken, PsiMethod.class);
            if (method == null) {
                return;
            }
            final PsiManager manager = method.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiAnnotation annotation =
                    factory.createAnnotationFromText(
                            '@' + AnnotationUtil.NULLABLE, method);
            final PsiModifierList modifierList = method.getModifierList();
            modifierList.addAfter(annotation, null);
            final CodeStyleManager styleManager = manager.getCodeStyleManager();
            styleManager.shortenClassReferences(modifierList);
        }
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message(
                "return.of.null.problem.descriptor");
    }

    public JComponent createOptionsPanel() {
        final GridBagLayout layout = new GridBagLayout();
        final JPanel panel = new JPanel(layout);
        final JCheckBox arrayCheckBox = new JCheckBox(
                InspectionGadgetsBundle.message(
                        "return.of.null.arrays.option"),
                m_reportArrayMethods);
        final ButtonModel arrayModel = arrayCheckBox.getModel();
        arrayModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_reportArrayMethods = arrayModel.isSelected();
            }
        });
        final JCheckBox objectCheckBox = new JCheckBox(
                InspectionGadgetsBundle.message(
                        "return.of.null.objects.option"),
                m_reportObjectMethods);
        final ButtonModel model = objectCheckBox.getModel();
        model.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_reportObjectMethods = model.isSelected();
            }
        });
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        panel.add(arrayCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weighty = 1.0;
        panel.add(objectCheckBox, constraints);
        return panel;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ReturnNullVisitor();
    }

    private class ReturnNullVisitor extends StatementInspectionVisitor {

        public void visitLiteralExpression(
                @NotNull PsiLiteralExpression value) {
            super.visitLiteralExpression(value);
            final String text = value.getText();
            if (!PsiKeyword.NULL.equals(text)) {
                return;
            }
            PsiElement parent = value.getParent();
            while (parent != null &&
                   (parent instanceof PsiParenthesizedExpression ||
                    parent instanceof PsiConditionalExpression ||
                    parent instanceof PsiTypeCastExpression)) {
                parent = parent.getParent();
            }
            if (parent == null || !(parent instanceof PsiReturnStatement)) {
                return;
            }
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(value, PsiMethod.class);
            if (method == null) {
                return;
            }
            final PsiType returnType = method.getReturnType();
            if (returnType == null) {
                return;
            }
            final boolean isArray = returnType.getArrayDimensions() > 0;
            if (AnnotationUtil.isAnnotated(method, AnnotationUtil.NULLABLE,
                    false)) {
                return;
            }
            if (m_reportArrayMethods && isArray) {
                registerError(value);
            }
            if (m_reportObjectMethods && !isArray) {
                registerError(value);
            }
        }
    }
}