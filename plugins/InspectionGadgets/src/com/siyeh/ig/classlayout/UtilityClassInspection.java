package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.UtilityClassUtil;

public class UtilityClassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Utility class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class #ref has only 'static' members, indicating procedural construction #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UtilityClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class UtilityClassVisitor extends BaseInspectionVisitor {
        private UtilityClassVisitor(BaseInspection inspection,
                                    InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!UtilityClassUtil.isUtilityClass(aClass)) {
                return;
            }
            registerClassError(aClass);
        }
    }
}
