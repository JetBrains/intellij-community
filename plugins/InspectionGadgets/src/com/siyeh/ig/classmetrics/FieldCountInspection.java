package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;

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
    private boolean m_countConstantFields = true;

    public String getDisplayName() {
        return "Class with too many fields";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return FIELD_COUNT_LIMIT;
    }

    protected String getConfigurationLabel() {
        return "Field count limit:";
    }

    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final String configurationLabel = getConfigurationLabel();
        final JLabel label = new JLabel(configurationLabel);
        final NumberFormat formatter = NumberFormat.getIntegerInstance();
        formatter.setParseIntegerOnly(true);
        final JFormattedTextField valueField = new JFormattedTextField(formatter);
        valueField.setValue(new Integer(m_limit));
        valueField.setColumns(4);
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

        final JCheckBox checkBox = new JCheckBox("Include constant fields", m_countConstantFields);
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
        return "#ref has too many fields (field count = " + count + ") #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new FieldCountVisitor(this, inspectionManager, onTheFly);
    }

    private class FieldCountVisitor extends BaseInspectionVisitor {
        private FieldCountVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
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
        for (int i = 0; i < fields.length; i++) {
            final PsiField field = fields[i];
            if (m_countConstantFields) {
                totalFields++;
            } else {
                if (!fieldIsConstant(field)) {
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
        if (!ClassUtils.isImmutable(type)) {
            return false;
        }
        return true;
    }

}
