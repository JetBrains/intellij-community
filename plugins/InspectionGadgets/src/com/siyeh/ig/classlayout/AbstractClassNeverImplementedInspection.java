package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;

public class AbstractClassNeverImplementedInspection extends ClassInspection {

    public String getDisplayName() {
        return "Abstract class which has no concrete subclass";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Abstract class #ref has no concrete subclass #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AbstractClassNeverImplementedVisitor(this, inspectionManager, onTheFly);
    }

    private static class AbstractClassNeverImplementedVisitor extends BaseInspectionVisitor {
        private AbstractClassNeverImplementedVisitor(BaseInspection inspection,
                                                           InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
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
            for(int i = 0; i < inheritors.length; i++){
                final PsiClass inheritor = inheritors[i];
                if(!inheritor.isInterface() && !inheritor.isAnnotationType() &&
                           !inheritor.hasModifierProperty(PsiModifier.ABSTRACT)){
                    return true;
                }
            }

            return false;
        }

    }

}
