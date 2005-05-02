package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MoveAnonymousToInnerClassFix;

public class AnonymousClassMethodCountInspection
        extends ClassMetricInspection {
    public String getID(){
        return "AnonymousInnerClassWithTooManyMethods";
    }
    private static final int DEFAULT_METHOD_COUNT_LIMIT = 1;
    private final MoveAnonymousToInnerClassFix fix = new MoveAnonymousToInnerClassFix();

    public String getDisplayName() {
        return "Anonymous inner class with too many methods";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return DEFAULT_METHOD_COUNT_LIMIT;
    }

    protected String getConfigurationLabel() {
        return "Method count limit:";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final int count = calculateTotalMethodCount(aClass);
        return "Anonymous inner class with too many methods (method count = " + count + ") #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MethodCountVisitor(this, inspectionManager, onTheFly);
    }

    private class MethodCountVisitor extends BaseInspectionVisitor {
        private MethodCountVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass psiClass) {
            // no call to super, to prevent double counting
        }

        public void visitAnonymousClass(PsiAnonymousClass aClass) {
            final int totalMethodCount = calculateTotalMethodCount(aClass);
            if (totalMethodCount <= getLimit()) {
                return;
            }
            final PsiJavaCodeReferenceElement classNameIdentifier =
                    aClass.getBaseClassReference();
            registerError(classNameIdentifier);
        }


    }

    private static int calculateTotalMethodCount(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getMethods();
        int totalCount = 0;
        for(final PsiMethod method : methods){
            if(!method.isConstructor()){
                totalCount++;
            }
        }
        return totalCount;
    }

}
