package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;
import java.text.NumberFormat;

public class ClassCouplingInspection
        extends ClassMetricInspection {

    private static final int DEFAULT_COUPLING_LIMIT = 15;
    /** @noinspection PublicField*/
    public boolean m_includeJavaClasses = false;
    /** @noinspection PublicField*/
    public boolean m_includeLibraryClasses = false;

    public String getID(){
        return "OverlyCoupledClass";
    }
    public String getDisplayName() {
        return "Overly coupled class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return DEFAULT_COUPLING_LIMIT;
    }

    protected String getConfigurationLabel() {
        return "Class coupling limit:";
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

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(valueField, constraints);

        final JCheckBox arrayCheckBox = new JCheckBox("Include couplings to java system classes", m_includeJavaClasses);
        final ButtonModel arrayModel = arrayCheckBox.getModel();
        arrayModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_includeJavaClasses = arrayModel.isSelected();
            }
        });
        final JCheckBox objectCheckBox = new JCheckBox("Include couplings to library classes", m_includeLibraryClasses);
        final ButtonModel model = objectCheckBox.getModel();
        model.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_includeLibraryClasses = model.isSelected();
            }
        });
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(arrayCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        panel.add(objectCheckBox, constraints);
        return panel;
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final int totalDependencies = calculateTotalDependencies(aClass);
        return "#ref is overly coupled (dependencies = " + totalDependencies + ") #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassCouplingVisitor(this, inspectionManager, onTheFly);
    }

    private class ClassCouplingVisitor extends BaseInspectionVisitor {
        private ClassCouplingVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // note: no call to super
            final int totalDependencies = calculateTotalDependencies(aClass);
            if (totalDependencies <= getLimit()) {
                return;
            }
            registerClassError(aClass);
        }
    }

    private int calculateTotalDependencies(PsiClass aClass) {
        final CouplingVisitor visitor = new CouplingVisitor(aClass, m_includeJavaClasses, m_includeLibraryClasses);
        aClass.accept(visitor);
        return visitor.getNumDependencies();
    }

}
