package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;

public class ConstructorCountInspection
        extends ClassMetricInspection {
    private static final int CONSTRUCTOR_COUNT_LIMIT = 5;

    public String getID(){
        return "ClassWithTooManyConstructors";
    }
    public String getDisplayName() {
        return "Class with too many constructors";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return CONSTRUCTOR_COUNT_LIMIT;
    }

    protected String getConfigurationLabel() {
        return "Constructor count limit:";
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final int count = calculateTotalConstructorCount(aClass);
        return "#ref has too many constructors (constructor count = " + count + ") #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MethodCountVisitor(this, inspectionManager, onTheFly);
    }

    private class MethodCountVisitor extends BaseInspectionVisitor {
        private MethodCountVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // note: no call to super
            final int totalComplexity = calculateTotalConstructorCount(aClass);
            if (totalComplexity <= getLimit()) {
                return;
            }
            registerClassError(aClass);
        }
    }

    private static int calculateTotalConstructorCount(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getMethods();
        int totalCount = 0;
        for (int i = 0; i < methods.length; i++) {
            final PsiMethod method = methods[i];
            if (method.isConstructor()) {
                totalCount++;
            }
        }
        return totalCount;
    }

}
