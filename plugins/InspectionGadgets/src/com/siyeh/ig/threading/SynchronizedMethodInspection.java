package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class SynchronizedMethodInspection extends MethodInspection {
    public boolean m_includeNativeMethods = true;

    public String getDisplayName() {
        return "'synchronized' method";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiModifierList modifierList = (PsiModifierList) location.getParent();
        final PsiMethod method = (PsiMethod) modifierList.getParent();
        return "Method " + method.getName() + "() declared '#ref' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SynchronizedMethodVisitor(this, inspectionManager, onTheFly);
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Include native methods",
                this, "m_includeNativeMethods");
    }

    private class SynchronizedMethodVisitor extends BaseInspectionVisitor {
        private SynchronizedMethodVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (!method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                return;
            }
            if (!m_includeNativeMethods &&
                    method.hasModifierProperty(PsiModifier.NATIVE)) {
                return;
            }
            registerModifierError(PsiModifier.SYNCHRONIZED, method);
        }

    }

}
