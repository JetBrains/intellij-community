package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MoveAnonymousToInnerClassFix;

public class AnonymousInnerClassMayBeStaticInspection extends ClassInspection {
    private final MoveAnonymousToInnerClassFix fix = new MoveAnonymousToInnerClassFix() ;

    public String getDisplayName() {
        return "Anonymous inner class may be a named static inner class";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Anonymous inner class #ref may be name static inner class #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }


    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AnonymousInnerClassCanBeStaticVisitor(this, inspectionManager,
                onTheFly);
    }

    private static class AnonymousInnerClassCanBeStaticVisitor
            extends BaseInspectionVisitor {
        private AnonymousInnerClassCanBeStaticVisitor(BaseInspection inspection,
                                             InspectionManager inspectionManager,
                                             boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass){
            if(aClass instanceof PsiAnonymousClass)
            {
                final PsiAnonymousClass anAnonymousClass = (PsiAnonymousClass) aClass;
                final InnerClassReferenceVisitor visitor =
                        new InnerClassReferenceVisitor(anAnonymousClass);
                anAnonymousClass.accept(visitor);
                if(!visitor.areReferenceStaticallyAccessible()){
                    return;
                }
                final PsiJavaCodeReferenceElement classNameIdentifier =
                        anAnonymousClass.getBaseClassReference();
                if(classNameIdentifier == null){
                    return;
                }
                registerError(classNameIdentifier);
            }
        }

    }
}