package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;

public class ConstantDeclaredInInterfaceInspection extends FieldInspection {

    public String getDisplayName() {
        return "Constant declared in interface";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Constant '#ref' declared in interface #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ConstantDeclaredInInterfaceVisitor(this, inspectionManager, onTheFly);
    }

    private static class ConstantDeclaredInInterfaceVisitor extends BaseInspectionVisitor {
        private ConstantDeclaredInInterfaceVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(PsiField field) {
            //no call to super, so we don't drill into anonymous classes
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!containingClass.isInterface() && !containingClass.isAnnotationType()) {
                return;
            }
            registerFieldError(field);
        }
    }
}
