package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;

public class AnonymousInnerClassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Anonymous inner class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Anonymous inner class #ref #loc";
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