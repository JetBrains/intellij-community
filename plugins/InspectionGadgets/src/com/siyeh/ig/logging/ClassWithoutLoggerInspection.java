package com.siyeh.ig.logging;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.siyeh.ig.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;
import java.text.NumberFormat;

public class ClassWithoutLoggerInspection extends ClassInspection {

    public String loggerClassName = "java.util.logging.Logger";

    public String getDisplayName() {
        return "Class without logger";
    }

    public String getGroupDisplayName() {
        return GroupNames.LOGGING_GROUP_NAME;
    }


    public JComponent createOptionsPanel() {
        final GridBagLayout layout = new GridBagLayout();
        final JPanel panel = new JPanel(layout);

        final JLabel classNameLabel = new JLabel("Logger class name:");
        classNameLabel.setHorizontalAlignment(SwingConstants.TRAILING);

        final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
        numberFormat.setParseIntegerOnly(true);
        numberFormat.setMinimumIntegerDigits(1);
        numberFormat.setMaximumIntegerDigits(2);

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
                loggerClassName =  loggerClassNameField.getText();
            }
        };
        final Document loggerClassNameDocument = loggerClassNameField.getDocument();
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
    public String buildErrorString(PsiElement location) {
        return "Class #ref does not declare a logger #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassWithoutLoggerVisitor(this, inspectionManager, onTheFly);
    }

    private class ClassWithoutLoggerVisitor extends BaseInspectionVisitor {

        private ClassWithoutLoggerVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            //no recursion to avoid drilldown
            if(aClass.isInterface() || aClass.isEnum()|| aClass.isInterface())
            {
                return;
            }
            if(aClass.getContainingClass()!=null)
            {
                return;
            }
            final PsiField[] fields = aClass.getFields();
            for (int i = 0; i < fields.length; i++) {
                if(isLogger(fields[i]))
                {
                    return;
                }
            }
            registerClassError(aClass);
        }

        private boolean isLogger(PsiField field) {
            final PsiType type = field.getType();
            if(type == null)
            {
                return false;
            }
            final String text = type.getCanonicalText();
            return text.equals(loggerClassName);
        }

    }

}
