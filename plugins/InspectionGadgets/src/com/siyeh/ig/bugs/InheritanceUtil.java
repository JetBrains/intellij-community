package com.siyeh.ig.bugs;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;

class InheritanceUtil{
    private InheritanceUtil(){
        super();
    }

    public static boolean existsMutualSubclass(PsiClass class1,
                                                      PsiClass class2){
        final String className = class1.getQualifiedName();
        if("java.lang.Object".equals(className)){
            return true;
        }
        final String class2Name = class2.getQualifiedName();
        if("java.lang.Object".equals(class2Name)){
            return true;
        }
        if(class1.isInheritor(class2, true) || class2.isInheritor(class1, true)){
            return true;
        }
        final PsiManager psiManager = class1.getManager();
        final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
        final SearchScope searchScope = class1.getUseScope();
        final PsiClass[] inheritors =
                searchHelper.findInheritors(class1, searchScope, true);
        for(int i = 0; i < inheritors.length; i++){
            final PsiClass inheritor = inheritors[i];
            if(inheritor.equals(class2) ||
                       inheritor.isInheritor(class2, true)){
                return true;
            }
        }
        return false;
    }
}
