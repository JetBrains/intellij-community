package com.siyeh.ig.classmetrics;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new MethodCountVisitor();
    }

    private class MethodCountVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
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
        for(final PsiMethod method : methods){
            if(method.isConstructor()){
                totalCount++;
            }
        }
        return totalCount;
    }

}
