package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class CloneableImplementsCloneInspection extends ClassInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreCloneableDueToInheritance = false;

    public String getID(){
        return "CloneableClassWithoutClone";
    }
    public String getDisplayName() {
        return "Cloneable class without 'clone()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLONEABLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref doesn't define clone() #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore classes cloneable due to inheritance",
                this, "m_ignoreCloneableDueToInheritance");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CloneableDefinesCloneVisitor(this, inspectionManager, onTheFly);
    }

    private class CloneableDefinesCloneVisitor extends BaseInspectionVisitor {
        private CloneableDefinesCloneVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface()  || aClass.isAnnotationType()) {
                return;
            }
            if (m_ignoreCloneableDueToInheritance) {
                if (!CloneUtils.isDirectlyCloneable(aClass)) {
                    return;
                }
            } else {
                if (!CloneUtils.isCloneable(aClass)) {
                    return;
                }
            }
            final PsiMethod[] methods = aClass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                final PsiMethod method = methods[i];
                if (CloneUtils.isClone(method)) {
                    return;
                }
            }
            registerClassError(aClass);
        }

    }

}
