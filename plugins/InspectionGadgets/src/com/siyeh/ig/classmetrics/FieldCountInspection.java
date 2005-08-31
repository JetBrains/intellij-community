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
package com.siyeh.ig.classmetrics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.FormattedTextFieldMacFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;
import java.text.NumberFormat;

public class FieldCountInspection
        extends ClassMetricInspection {
    private static final int FIELD_COUNT_LIMIT = 10;
    /** @noinspection PublicField*/
    public boolean m_countConstantFields = false;

    public String getID(){
        return "ClassWithTooManyFields";
    }
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("too.many.fields.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return FIELD_COUNT_LIMIT;
    }

    protected String getConfigurationLabel() {
        return InspectionGadgetsBundle.message("too.many.fields.count.limit.option");
    }

    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final String configurationLabel = getConfigurationLabel();
        final JLabel label = new JLabel(configurationLabel);
        final NumberFormat formatter = NumberFormat.getIntegerInstance();
        formatter.setParseIntegerOnly(true);
        final JFormattedTextField valueField = new JFormattedTextField(formatter);
        valueField.setValue(m_limit);
        valueField.setColumns(4);
        FormattedTextFieldMacFix.apply(valueField);
        final Document document = valueField.getDocument();
        document.addDocumentListener(new DocumentListener() {
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
                m_limit = ((Number) valueField.getValue()).intValue();
            }
        });

        final JCheckBox checkBox = new JCheckBox(InspectionGadgetsBundle.message("too.many.fields.include.constant.option"), m_countConstantFields);
        final ButtonModel model = checkBox.getModel();
        model.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_countConstantFields = model.isSelected();
            }
        });
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.gridwidth = 3;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(valueField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 4;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(checkBox, constraints);
        return panel;
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final int count = countFields(aClass);
        return InspectionGadgetsBundle.message("too.many.fields.problem.descriptor", count);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FieldCountVisitor();
    }

    private class FieldCountVisitor extends BaseInspectionVisitor {
    
        public void visitClass(@NotNull PsiClass aClass) {
            // note: no call to super
            final int totalFields = countFields(aClass);
            if (totalFields <= getLimit()) {
                return;
            }
            registerClassError(aClass);
        }
    }

    private int countFields(PsiClass aClass) {
        int totalFields = 0;
        final PsiField[] fields = aClass.getFields();
        for(final PsiField field : fields){
            if(m_countConstantFields){
                totalFields++;
            } else{
                if(!fieldIsConstant(field)){
                    totalFields++;
                }
            }
        }
        return totalFields;
    }

    private static boolean fieldIsConstant(PsiField field) {
        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
        }
        final PsiType type = field.getType();
        return ClassUtils.isImmutable(type);
    }

}
