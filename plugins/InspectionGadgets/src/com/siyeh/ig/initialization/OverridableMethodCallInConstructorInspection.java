package com.siyeh.ig.initialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class OverridableMethodCallInConstructorInspection extends MethodInspection {

    public String getDisplayName() {
        return "Overridable method call in constructor";
    }

    public String getGroupDisplayName() {
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to overridable method #ref during object construction #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AbstractMethodCallInConstructorVisitor(this, inspectionManager, onTheFly);
    }

    private static class AbstractMethodCallInConstructorVisitor extends BaseInspectionVisitor {
        private AbstractMethodCallInConstructorVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiMethod method =
                    (PsiMethod) PsiTreeUtil.getParentOfType(call,
                                                            PsiMethod.class);
            if(method == null) {
                return;
            }
            if (!method.isConstructor()) {
                return;
            }
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            if (methodExpression.isQualified() &&
                !(methodExpression.getQualifierExpression() instanceof PsiThisExpression))
            {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiMethod calledMethod = (PsiMethod) methodExpression.resolve();
            if (calledMethod == null) {
                return;
            }
            if (!isOverridable(calledMethod)) {
                return;
            }
            final PsiClass calledMethodClass = calledMethod.getContainingClass();
            if (!InheritanceUtil.isInheritorOrSelf(containingClass, calledMethodClass, true))
            {
                return;
            }
            registerMethodCallError(call);
        }

        private static boolean isOverridable(PsiMethod calledMethod) {
            return !calledMethod.isConstructor() &&
                    !calledMethod.hasModifierProperty(PsiModifier.FINAL) &&
                    !calledMethod.hasModifierProperty(PsiModifier.STATIC) &&
                    !calledMethod.hasModifierProperty(PsiModifier.PRIVATE);
        }
    }
}
