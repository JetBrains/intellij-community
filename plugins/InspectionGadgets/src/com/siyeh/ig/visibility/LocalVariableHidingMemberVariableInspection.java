package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class LocalVariableHidingMemberVariableInspection extends MethodInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreInvisibleFields = true;
    /** @noinspection PublicField*/
    public boolean m_ignoreStaticMethods = true;
    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "LocalVariableHidesMemberVariable";
    }

    public String getDisplayName() {
        return "Local variable hides member variable";
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
        return "Local variable '#ref' hides member variable #loc";
    }

    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JCheckBox ignoreInvisibleCheckBox = new JCheckBox("Ignore superclass fields not visible from subclass",
                m_ignoreInvisibleFields);
        final JCheckBox ignoreStaticCheckBox = new JCheckBox("Ignore local variables in static methods",
                m_ignoreStaticMethods);

        final ButtonModel ignoreInvisibleModel = ignoreInvisibleCheckBox.getModel();
        ignoreInvisibleModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_ignoreInvisibleFields = ignoreInvisibleModel.isSelected();
            }
        });
        final ButtonModel ignoreStaticModel = ignoreStaticCheckBox.getModel();
        ignoreStaticModel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                m_ignoreStaticMethods = ignoreStaticModel.isSelected();
            }
        });
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(ignoreInvisibleCheckBox, constraints);
        constraints.gridy = 1;
        panel.add(ignoreStaticCheckBox, constraints);
        return panel;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new LocalVariableHidingMemberVariableVisitor(this, inspectionManager, onTheFly);
    }

    private class LocalVariableHidingMemberVariableVisitor extends BaseInspectionVisitor {
        private LocalVariableHidingMemberVariableVisitor(BaseInspection inspection,
                                                         InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            if (m_ignoreStaticMethods) {
                final PsiMethod aMethod =
                        PsiTreeUtil.getParentOfType(variable,
                                                                PsiMethod.class);
                if (aMethod == null) {
                    return;
                }
                if (aMethod.hasModifierProperty(PsiModifier.STATIC)) {
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
            for(final PsiField field : fields){
                if(checkFieldName(field, variableName, aClass)){
                    registerVariableError(variable);
                }
            }
        }

        public void visitParameter(@NotNull PsiParameter variable) {
            super.visitParameter(variable);
            if (!(variable.getDeclarationScope() instanceof PsiCatchSection)) {
                return;
            }
            final PsiClass aClass =
                    ClassUtils.getContainingClass(variable);
            if (aClass == null) {
                return;
            }
            final String variableName = variable.getName();
            final PsiField[] fields = aClass.getAllFields();
            for(final PsiField field : fields){
                if(checkFieldName(field, variableName, aClass)){
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
            return !m_ignoreInvisibleFields ||
                           ClassUtils.isFieldVisible(field, aClass);
        }

    }

}
