package com.siyeh.ig.j2me;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.performance.InnerClassReferenceVisitor;
import com.siyeh.ig.fixes.MoveAnonymousToInnerClassFix;
import org.jetbrains.annotations.NotNull;

public class AnonymousInnerClassMayBeStaticInspection extends ClassInspection {
    private final MoveAnonymousToInnerClassFix fix = new MoveAnonymousToInnerClassFix() ;

    public String getDisplayName() {
        return "Anonymous inner class may be a named static inner class";
    }

    public String getGroupDisplayName() {
        return GroupNames.J2ME_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Anonymous inner class #ref may be name static inner class #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }


    public BaseInspectionVisitor buildVisitor() {
        return new AnonymousInnerClassCanBeStaticVisitor();
    }

    private static class AnonymousInnerClassCanBeStaticVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass){
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