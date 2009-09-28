/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.RegExInputVerifier;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;

public class NonStaticFinalLoggerInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public String loggerClassName = "java.util.logging.Logger";

    @NotNull
    public String getID(){
        return "NonConstantLogger";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "non.constant.logger.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "non.constant.logger.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new NonStaticFinalLoggerFix();
    }

    private static class NonStaticFinalLoggerFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "non.constant.logger.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiField)) {
                return;
            }
            PsiField field = (PsiField) parent;
            final PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null) {
                return;
            }
            modifierList.setModifierProperty(PsiModifier.FINAL, true);
            modifierList.setModifierProperty(PsiModifier.STATIC, true);
        }
    }


    public JComponent createOptionsPanel() {
        final GridBagLayout layout = new GridBagLayout();
        final JPanel panel = new JPanel(layout);

        final JLabel classNameLabel = new JLabel(
                InspectionGadgetsBundle.message("logger.name.option"));
        classNameLabel.setHorizontalAlignment(SwingConstants.TRAILING);

        final JTextField loggerClassNameField = new JTextField();
        final Font panelFont = panel.getFont();
        loggerClassNameField.setFont(panelFont);
        loggerClassNameField.setText(loggerClassName);
        loggerClassNameField.setColumns(100);
        loggerClassNameField.setInputVerifier(new RegExInputVerifier());
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
                loggerClassName = loggerClassNameField.getText();
            }
        };
        final Document loggerClassNameDocument =
                loggerClassNameField.getDocument();
        loggerClassNameDocument.addDocumentListener(listener);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(classNameLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(loggerClassNameField, constraints);

        return panel;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonStaticFinalLoggerVisitor();
    }

    private class NonStaticFinalLoggerVisitor extends BaseInspectionVisitor {

        @Override public void visitClass(@NotNull PsiClass aClass) {
            //no recursion to avoid drilldown
            if (aClass.isInterface() || aClass.isEnum() ||
                    aClass.isAnnotationType()) {
                return;
            }
            if(aClass instanceof PsiTypeParameter) {
                return;
            }
            if (aClass.getContainingClass() != null) {
                return;
            }
            final PsiField[] fields = aClass.getFields();
            for(final PsiField field : fields) {
                if(isLogger(field)) {
                    if(!field.hasModifierProperty(PsiModifier.STATIC) ||
                            !field.hasModifierProperty(PsiModifier.FINAL)){
                        registerFieldError(field);
                    }
                }
            }
        }

        private boolean isLogger(PsiField field) {
            final PsiType type = field.getType();
            final String text = type.getCanonicalText();
            return text.equals(loggerClassName);
        }
    }
}