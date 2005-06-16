package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class CloneDeclaresCloneNotSupportedInspection extends MethodInspection{
    private final CloneDeclaresCloneNotSupportedInspectionFix fix = new CloneDeclaresCloneNotSupportedInspectionFix();

    public String getID(){
        return "CloneDoesntDeclareCloneNotSupportedException";
    }

    public String getDisplayName(){
        return "'clone()' doesn't declare CloneNotSupportedException";
    }

    public String getGroupDisplayName(){
        return GroupNames.CLONEABLE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "#ref() doesn't declare CloneNotSupportedException #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class CloneDeclaresCloneNotSupportedInspectionFix
                                                                     extends InspectionGadgetsFix{
        public String getName(){
            return "Declare CloneNotSupportedException";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{

            final PsiElement methodNameIdentifier = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod) methodNameIdentifier.getParent();
            PsiUtil.addException(method,
                                 "java.lang.CloneNotSupportedException");
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new CloneDeclaresCloneNotSupportedExceptionVisitor();
    }

    private static class CloneDeclaresCloneNotSupportedExceptionVisitor
                                                                        extends BaseInspectionVisitor{
        public void visitMethod(@NotNull PsiMethod method){
            //note: no call to super;
            final String methodName = method.getName();
            if(!"clone".equals(methodName)){
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList == null){
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters == null || parameters.length != 0){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null)
            {
                return;
            }
            if(containingClass.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            final PsiReferenceList throwsList = method.getThrowsList();
            if(throwsList == null){
                registerMethodError(method);
                return;
            }
            final PsiJavaCodeReferenceElement[] referenceElements = throwsList.getReferenceElements();
            for(final PsiJavaCodeReferenceElement referenceElement : referenceElements){
                final PsiElement referencedElement = referenceElement.resolve();
                if(referencedElement != null &&
                        referencedElement instanceof PsiClass){
                    final PsiClass aClass = (PsiClass) referencedElement;
                    final String className = aClass.getQualifiedName();
                    if("java.lang.CloneNotSupportedException".equals(className)){
                        return;
                    }
                }
            }

            registerMethodError(method);
        }
    }
}
