package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MoveAnonymousToInnerClassFix;
import org.jetbrains.annotations.NotNull;

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

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }
    public BaseInspectionVisitor buildVisitor() {
        return new AnonymousInnerClassVisitor();
    }

    private static class AnonymousInnerClassVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
           //no call to super here, to avoid double counting
        }

        public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
            super.visitAnonymousClass(aClass);
            final PsiJavaCodeReferenceElement classReference = aClass.getBaseClassReference();
            registerError(classReference);
        }
    }
}