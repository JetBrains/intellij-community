package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;

public class LimitedScopeInnerClassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Limited-scope class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Limited-scope inner class #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new LimitedScopeInnerClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class LimitedScopeInnerClassVisitor extends BaseInspectionVisitor {
        private LimitedScopeInnerClassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            if (aClass.getParent() instanceof PsiDeclarationStatement) {
                registerClassError(aClass);
            }
        }
    }
}