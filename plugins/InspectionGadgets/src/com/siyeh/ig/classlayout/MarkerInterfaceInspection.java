package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;

public class MarkerInterfaceInspection extends ClassInspection {

    public String getDisplayName() {
        return "Marker interface";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Marker interface #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MarkerInterfaceVisitor(this, inspectionManager, onTheFly);
    }

    private static class MarkerInterfaceVisitor extends BaseInspectionVisitor {
        private MarkerInterfaceVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            final PsiField[] fields = aClass.getFields();
            if (fields.length != 0) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            if (methods.length != 0) {
                return;
            }
            final PsiClassType[] extendsList = aClass.getExtendsListTypes();
            if (extendsList.length > 1) {
                return;
            }
            registerClassError(aClass);
        }
    }
}
