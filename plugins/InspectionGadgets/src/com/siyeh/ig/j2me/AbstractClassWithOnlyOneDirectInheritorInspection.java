package com.siyeh.ig.j2me;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import org.jetbrains.annotations.NotNull;

public class AbstractClassWithOnlyOneDirectInheritorInspection extends ClassInspection {

    public String getDisplayName() {
        return "Abstract class which has only one direct inheritor";
    }

    public String getGroupDisplayName() {
        return GroupNames.J2ME_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Abstract class #ref has has only one direct inheritor #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InterfaceNeverImplementedVisitor();
    }

    private static class InterfaceNeverImplementedVisitor extends BaseInspectionVisitor {


        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (aClass.isInterface() || aClass.isAnnotationType()
                    || aClass.isEnum()) {
                return;
            }
            if(aClass instanceof PsiTypeParameter ||
                    aClass instanceof PsiAnonymousClass){
                return;
            }
            if(!aClass.hasModifierProperty(PsiModifier.ABSTRACT))
            {
                return;
            }
            if (!hasOneInheritor(aClass)) {
                return;
            }
            registerClassError(aClass);
        }

        private static boolean hasOneInheritor(PsiClass aClass) {
            final PsiManager psiManager = aClass.getManager();
            final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
            final SearchScope searchScope = aClass.getUseScope();
            final PsiClass[] inheritors =
                    searchHelper.findInheritors(aClass, searchScope, false);
            return inheritors.length == 1;
        }

    }

}
