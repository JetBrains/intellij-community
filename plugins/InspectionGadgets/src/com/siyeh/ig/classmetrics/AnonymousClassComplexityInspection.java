package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MoveAnonymousToInnerClassFix;

public class AnonymousClassComplexityInspection
        extends ClassMetricInspection {
    private static final int DEFAULT_COMPLEXITY_LIMIT = 3;
    private final MoveAnonymousToInnerClassFix fix = new MoveAnonymousToInnerClassFix();

    public String getDisplayName() {
        return "Overly complex anonymous inner class";
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

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final int totalComplexity = calculateTotalComplexity(aClass);
        return "Overly complex anonymous inner class(cyclomatic complexity = " + totalComplexity + ") #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassComplexityVisitor(this, inspectionManager, onTheFly);
    }

    private class ClassComplexityVisitor extends BaseInspectionVisitor {
        private ClassComplexityVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass psiClass) {
            // no call to super, to prevent double counting
        }

        public void visitAnonymousClass(PsiAnonymousClass aClass) {
            final int totalComplexity = calculateTotalComplexity(aClass);
            if (totalComplexity <= getLimit()) {
                return;
            }
            final PsiJavaCodeReferenceElement classNameIdentifier =
                    aClass.getBaseClassReference();
            registerError(classNameIdentifier);
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
