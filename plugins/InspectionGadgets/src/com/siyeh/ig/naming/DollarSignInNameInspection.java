package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;

public class DollarSignInNameInspection extends BaseInspection {
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return "Use of '$' in identifier";
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "identifer '#ref' contains $ #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    public ProblemDescriptor[] doCheckClass(PsiClass aClass, InspectionManager mgr, boolean isOnTheFly) {
        if (aClass instanceof PsiAnonymousClass) {
            return super.doCheckClass(aClass, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        aClass.accept(visitor);
        return visitor.getErrors();
    }

    public ProblemDescriptor[] doCheckMethod(PsiMethod method, InspectionManager mgr, boolean isOnTheFly) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return super.doCheckMethod(method, mgr, isOnTheFly);
        }
        if (!containingClass.isPhysical()) {
            return super.doCheckMethod(method, mgr, isOnTheFly);
        }
        if (containingClass instanceof PsiAnonymousClass) {
            return super.doCheckClass(containingClass, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        method.accept(visitor);
        return visitor.getErrors();
    }

    public ProblemDescriptor[] doCheckField(PsiField field, InspectionManager mgr, boolean isOnTheFly) {
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return super.doCheckField(field, mgr, isOnTheFly);
        }
        if (!containingClass.isPhysical()) {
            return super.doCheckField(field, mgr, isOnTheFly);
        }
        if (containingClass instanceof PsiAnonymousClass) {
            return super.doCheckClass(containingClass, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        field.accept(visitor);
        return visitor.getErrors();
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new DollarSignInNameVisitor(this, inspectionManager, onTheFly);
    }

    private static class DollarSignInNameVisitor extends BaseInspectionVisitor {
        private DollarSignInNameVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitVariable(PsiVariable variable) {
            super.visitVariable(variable);
            final String name = variable.getName();
            if (name == null) {
                return;
            }
            if (name.indexOf((int) '$') < 0) {
                return;
            }
            registerVariableError(variable);
        }

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            final String name = method.getName();
            if (name == null) {
                return;
            }
            if (name.indexOf((int) '$') < 0) {
                return;
            }
            registerMethodError(method);
        }

        public void visitClass(PsiClass aClass) {
            //note: no call to super, to avoid drill-down
            final String name = aClass.getName();
            if (name == null) {
                return;
            }
            if (name.indexOf((int) '$') < 0) {
                return;
            }
            registerClassError(aClass);
        }
    }

}
