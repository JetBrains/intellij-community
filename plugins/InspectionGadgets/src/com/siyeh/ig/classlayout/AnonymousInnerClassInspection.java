package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MoveAnonymousToInnerClassFix;

public class AnonymousInnerClassInspection extends ClassInspection {
    private final MoveAnonymousToInnerClassFix fix =
            new MoveAnonymousToInnerClassFix();

    public String getDisplayName() {
        return "Anonymous inner class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Anonymous inner class #ref #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AnonymousInnerClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class AnonymousInnerClassVisitor extends BaseInspectionVisitor {
        private AnonymousInnerClassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
           //no call to super here, to avoid double counting
        }

        public void visitAnonymousClass(PsiAnonymousClass aClass) {
            super.visitAnonymousClass(aClass);
            final PsiJavaCodeReferenceElement classReference = aClass.getBaseClassReference();
            registerError(classReference);
        }
    }
}