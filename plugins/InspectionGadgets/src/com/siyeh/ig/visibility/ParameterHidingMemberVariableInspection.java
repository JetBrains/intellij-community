package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class ParameterHidingMemberVariableInspection extends MethodInspection {
    public boolean m_ignoreInvisibleFields = false;
    public boolean m_ignoreStaticMethodParametersHidingInstanceFields = false;
    public boolean m_ignoreForConstructors = false;
    public boolean m_ignoreForPropertySetters = false;
    public boolean m_ignoreForAbstractMethods = false;
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return "Parameter hides member variable";
    }

    public String getGroupDisplayName() {
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "Parameter '#ref' hides member variable #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ParameterHidingMemberVariableVisitor(this, inspectionManager, onTheFly);
    }

    private class ParameterHidingMemberVariableVisitor extends BaseInspectionVisitor {
        private ParameterHidingMemberVariableVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitParameter(PsiParameter variable) {
            super.visitParameter(variable);
            if (variable.getDeclarationScope() instanceof PsiTryStatement) {
                return;
            }
            final PsiMethod method =
                    (PsiMethod) PsiTreeUtil.getParentOfType(variable, PsiMethod.class);
            if (method == null) {
                return;
            }
            if (m_ignoreForConstructors && method.isConstructor()) {
                return;
            }
            if (m_ignoreForAbstractMethods &&
                    (method.hasModifierProperty(PsiModifier.ABSTRACT) || method.getContainingClass().isInterface())) {
                return;
            }
            if (m_ignoreForPropertySetters) {
                final String methodName = method.getName();
                final PsiType returnType = method.getReturnType();
                if (methodName.startsWith("set") && returnType.equals(PsiType.VOID)) {
                    return;
                }
            }

            final PsiClass aClass =
                    ClassUtils.getContainingClass(variable);
            if (aClass == null) {
                return;
            }
            final String variableName = variable.getName();
            final PsiField[] fields = aClass.getAllFields();
            for (int i = 0; i < fields.length; i++) {
                final PsiField field = fields[i];
                if (checkFieldName(field, variableName, aClass)) {
                    if (m_ignoreStaticMethodParametersHidingInstanceFields &&
                            !field.hasModifierProperty(PsiModifier.STATIC) &&
                            method.hasModifierProperty(PsiModifier.STATIC)) {
                        continue;
                    }
                    registerVariableError(variable);
                }
            }
        }

        private boolean checkFieldName(PsiField field, String variableName, PsiClass aClass) {
            if (field == null) {
                return false;
            }
            final String fieldName = field.getName();
            if (fieldName == null) {
                return false;
            }
            if (!fieldName.equals(variableName)) {
                return false;
            }
            if (m_ignoreInvisibleFields && !ClassUtils.isFieldVisible(field, aClass)) {
                return false;
            }
            return true;
        }

    }

    public JComponent createOptionsPanel() {
        final GridBagLayout layout = new GridBagLayout();
        final JPanel panel = new JPanel(layout);
        final JCheckBox settersCheckBox = new JCheckBox("Ignore for property setters", m_ignoreForPropertySetters);
        final ButtonModel settersModel = settersCheckBox.getModel();
        settersModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_ignoreForPropertySetters = settersModel.isSelected();
            }
        });
        final JCheckBox ignoreInvisibleFieldsCheck = new JCheckBox("Ignore superclass fields not visible from subclass",
                m_ignoreInvisibleFields);

        final ButtonModel invisibleFieldsModel = ignoreInvisibleFieldsCheck.getModel();
        invisibleFieldsModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_ignoreInvisibleFields = invisibleFieldsModel.isSelected();
            }
        });

        final JCheckBox constructorCheckBox = new JCheckBox("Ignore for constructors", m_ignoreForConstructors);
        final ButtonModel constructorModel = constructorCheckBox.getModel();
        constructorModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_ignoreForConstructors = constructorModel.isSelected();
            }
        });
        final JCheckBox abstractMethodsCheckbox = new JCheckBox("Ignore for abstract methods", m_ignoreForAbstractMethods);
        final ButtonModel abstractMethodsModel = abstractMethodsCheckbox.getModel();
        abstractMethodsModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_ignoreForAbstractMethods = abstractMethodsModel.isSelected();
            }
        });

        final JCheckBox staticMethodsCheckbox = new JCheckBox("Ignore for static method parameters hiding instance fields", m_ignoreStaticMethodParametersHidingInstanceFields);
        final ButtonModel staticMethodsModel = staticMethodsCheckbox.getModel();
        staticMethodsModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_ignoreStaticMethodParametersHidingInstanceFields = staticMethodsModel.isSelected();
            }
        });

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;

        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(settersCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        panel.add(constructorCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        panel.add(ignoreInvisibleFieldsCheck, constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        panel.add(staticMethodsCheckbox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 4;
        panel.add(abstractMethodsCheckbox, constraints);
        return panel;
    }
}
