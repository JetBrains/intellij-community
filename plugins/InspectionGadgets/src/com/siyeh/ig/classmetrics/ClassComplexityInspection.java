package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;

public class ClassComplexityInspection
        extends ClassMetricInspection {
    private static final int DEFAULT_COMPLEXITY_LIMIT = 80;

    public String getDisplayName() {
        return "Overly complex class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return DEFAULT_COMPLEXITY_LIMIT;
    }

    protected String getConfigurationLabel() {
        return "Cyclomatic complexity limit:";
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final int totalComplexity = calculateTotalComplexity(aClass);
        return "#ref is overly complex (cyclomatic complexity = " + totalComplexity + ") #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassComplexityVisitor(this, inspectionManager, onTheFly);
    }

    private class ClassComplexityVisitor extends BaseInspectionVisitor {
        private ClassComplexityVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // note: no call to super
            final int totalComplexity = calculateTotalComplexity(aClass);
            if (totalComplexity <= getLimit()) {
                return;
            }
            registerClassError(aClass);
        }

    }

    private static int calculateTotalComplexity(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getMethods();
        int totalComplexity = calculateComplexityForMethods(methods);
        totalComplexity += calculateInitializerComplexity(aClass);
        return totalComplexity;
    }

    private static int calculateInitializerComplexity(PsiClass aClass) {
        final ComplexityVisitor visitor = new ComplexityVisitor();
        int complexity = 0;
        final PsiClassInitializer[] initializers = aClass.getInitializers();
        for (int i = 0; i < initializers.length; i++) {
            final PsiClassInitializer initializer = initializers[i];
            visitor.reset();
            initializer.accept(visitor);
            complexity += visitor.getComplexity();
        }
        return complexity;
    }

    private static int calculateComplexityForMethods(PsiMethod[] methods) {
        final ComplexityVisitor visitor = new ComplexityVisitor();
        int complexity = 0;
        for (int i = 0; i < methods.length; i++) {
            final PsiMethod method = methods[i];
            visitor.reset();
            method.accept(visitor);
            complexity += visitor.getComplexity();
        }
        return complexity;
    }

}
