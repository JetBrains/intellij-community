package com.siyeh.ig.initialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class AbstractMethodCallInConstructorInspection extends MethodInspection {

    public String getDisplayName() {
        return "Abstract method call in constructor";
    }

    public String getGroupDisplayName() {
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to abstract method #ref during object construction #loc";
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
            final PsiMethod method = (PsiMethod) PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            if (method == null) {
                return;
            }
            if (!method.isConstructor()) {
                return;
            }
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final PsiMethod calledMethod = (PsiMethod) methodExpression.resolve();
            if (calledMethod == null) {
                return;
            }
            if (calledMethod.isConstructor()) {
                return;
            }
            if (!calledMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            final PsiClass calledMethodClass = calledMethod.getContainingClass();
            final PsiClass methodClass = method.getContainingClass();
            if (!calledMethodClass.equals(methodClass)) {
                return;
            }
            registerMethodCallError(call);
        }
    }
}
