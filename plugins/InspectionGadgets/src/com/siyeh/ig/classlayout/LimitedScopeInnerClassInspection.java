package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MoveClassFix;

public class LimitedScopeInnerClassInspection extends ClassInspection {

    private final MoveClassFix fix = new MoveClassFix();
    public String getDisplayName() {
        return "Limited-scope class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Limited-scope inner class #ref #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
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