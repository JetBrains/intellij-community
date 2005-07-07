package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import org.jetbrains.annotations.NotNull;

public class AbstractClassNeverImplementedInspection extends ClassInspection {

    public String getDisplayName() {
        return "Abstract class which has no concrete subclass";
    }

    public String getGroupDisplayName() {
        return GroupNames.INHERITANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Abstract class #ref has no concrete subclass #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AbstractClassNeverImplementedVisitor();
    }

    private static class AbstractClassNeverImplementedVisitor extends BaseInspectionVisitor {


        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (hasImplementation(aClass)) {
                return;
            }
            registerClassError(aClass);
        }

        private static boolean hasImplementation(PsiClass aClass) {
            final PsiManager psiManager = aClass.getManager();
            final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
            final SearchScope searchScope = aClass.getUseScope();
            final PsiClass[] inheritors =
                    searchHelper.findInheritors(aClass, searchScope, true);
            for(final PsiClass inheritor : inheritors){
                if(!inheritor.isInterface() && !inheritor.isAnnotationType() &&
                        !inheritor.hasModifierProperty(PsiModifier.ABSTRACT)){
                    return true;
                }
            }

            return false;
        }

    }

}
