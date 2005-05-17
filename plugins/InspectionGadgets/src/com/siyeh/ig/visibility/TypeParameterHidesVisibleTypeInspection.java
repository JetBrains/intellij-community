package com.siyeh.ig.visibility;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;

public class TypeParameterHidesVisibleTypeInspection extends ClassInspection{
    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "FieldNameHidesFieldInSuperclass";
    }

    public String getDisplayName(){
        return "Type parameter hides visible type";
    }

    public String getGroupDisplayName(){
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiTypeParameter parameter = (PsiTypeParameter) location.getParent();
        final PsiManager manager = parameter.getManager();
        final PsiFile containingFile = parameter.getContainingFile();
        final PsiResolveHelper resolveHelper = manager.getResolveHelper();
        final String unqualifiedClassName = parameter.getName();
        final PsiClass aClass =
                resolveHelper.resolveReferencedClass(unqualifiedClassName,
                                                     containingFile);
        return "Type parameter '#ref' hides a visible type '" +
                aClass .getQualifiedName() + "'#loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new TypeParameterHidesVisibleTypeVisitor();
    }

    private static class TypeParameterHidesVisibleTypeVisitor
            extends BaseInspectionVisitor{
     
        public void visitTypeParameter(PsiTypeParameter parameter){
            super.visitTypeParameter(parameter);
            final String unqualifiedClassName = parameter.getName();

            final PsiManager manager = parameter.getManager();
            final PsiFile containingFile = parameter.getContainingFile();
            final PsiResolveHelper resolveHelper = manager.getResolveHelper();
            final PsiClass aClass =
                    resolveHelper.resolveReferencedClass(unqualifiedClassName,
                                                         containingFile);
            if(aClass == null) {
                return;
            }
            final PsiIdentifier identifier = parameter.getNameIdentifier();
            registerError(identifier);
        }
    }
}
