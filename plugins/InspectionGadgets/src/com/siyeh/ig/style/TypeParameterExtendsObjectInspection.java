package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;

public class TypeParameterExtendsObjectInspection extends ClassInspection{
    private final ExtendsObjectFix fix = new ExtendsObjectFix();

    public String getID(){
        return "TypeParameterExplicitlyExtendsObject";
    }

    public String getDisplayName(){
        return "Type parameter explicitly extends java.lang.Object";
    }

    public String getGroupDisplayName(){
        return GroupNames.STYLE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "Type parameter '#ref' explicitly extends java.lang.Object #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class ExtendsObjectFix extends InspectionGadgetsFix{
        public String getName(){
            return "Remove redundant 'extends Object'";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{

            final PsiElement extendClassIdentifier = descriptor.getPsiElement();
            final PsiTypeParameter element =
                    (PsiTypeParameter) extendClassIdentifier.getParent();
            assert element != null;
            final PsiReferenceList extendsList = element.getExtendsList();
            final PsiJavaCodeReferenceElement[] elements =
                    extendsList.getReferenceElements();
            for(PsiJavaCodeReferenceElement element1 : elements){
                deleteElement(element1);
            }
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ExtendsObjectVisitor();
    }

    private static class ExtendsObjectVisitor extends BaseInspectionVisitor{

        public void visitTypeParameter(PsiTypeParameter parameter){
            super.visitTypeParameter(parameter);
            final PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
            final PsiReferenceList extendsList = parameter.getExtendsList();
            final PsiJavaCodeReferenceElement[] elements =
                    extendsList.getReferenceElements();
            for(final PsiJavaCodeReferenceElement element : elements){
                final String text = element.getText();
                if("Object".equals(text) ||
                        "java.lang.Object".equals(text)){
                    registerError(nameIdentifier);
                }
            }
        }
    }
}
