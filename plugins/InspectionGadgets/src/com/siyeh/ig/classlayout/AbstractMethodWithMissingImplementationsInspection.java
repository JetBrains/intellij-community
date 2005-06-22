package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;

public class AbstractMethodWithMissingImplementationsInspection
                                                                extends MethodInspection{
    public String getDisplayName(){
        return "Abstract method with missing implementations";
    }

    public String getGroupDisplayName(){
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Abstract method #ref is not implemented in every subclass #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new AbstactMethodWithMissingImplementationsVisitor();
    }

    private static class AbstactMethodWithMissingImplementationsVisitor
                                                                        extends BaseInspectionVisitor{
        public void visitMethod(PsiMethod method){
            super.visitMethod(method);
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return;
            }
            if(!containingClass.isInterface() &&
                    !method.hasModifierProperty(PsiModifier.ABSTRACT)){
                return;
            }
            final PsiManager psiManager = containingClass.getManager();
            final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
            final SearchScope searchScope = containingClass.getUseScope();
            final PsiClass[] inheritors =
                    searchHelper.findInheritors(containingClass, searchScope,
                                                true);
            for(final PsiClass inheritor : inheritors){
                if(!inheritor.isInterface() &&
                        !inheritor.hasModifierProperty(PsiModifier.ABSTRACT)){
                    if(!hasMatchingImplementation(inheritor, method)){
                        registerMethodError(method);
                        return;
                    }
                }
            }
        }

        private static boolean hasMatchingImplementation(PsiClass aClass,
                                                  PsiMethod method){
            final PsiMethod[] methods = aClass.findMethodsBySignature(method, true);
            for(final PsiMethod methodToMatch : methods){
                if(!methodToMatch.hasModifierProperty(PsiModifier.ABSTRACT) &&
                        !methodToMatch.getContainingClass().isInterface())
                {
                    return true;
                }
            }
            return false;
        }
    }
}
