package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class CallToNativeMethodWhileLockedInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Call to a native method while locked";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to native method #ref() in a synchronized context #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new WaitNotInSynchronizedContextVisitor(this, inspectionManager, onTheFly);
    }

    private static class WaitNotInSynchronizedContextVisitor extends BaseInspectionVisitor {
        private boolean m_inSynchronizedContext = false;

        private WaitNotInSynchronizedContextVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!m_inSynchronizedContext) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null)
            {
                return;
            }
            if (!method.hasModifierProperty(PsiModifier.NATIVE)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null)
            {
                return;
            }
            final String className = containingClass.getQualifiedName();
            if("java.lang.Object".equals(className))
            {
                return;
            }
            registerMethodCallError(expression);
        }

        public void visitMethod(PsiMethod method) {
            final boolean wasInSynchronizedContext = m_inSynchronizedContext;
            if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {

                m_inSynchronizedContext = true;
            }
            super.visitMethod(method);
            if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {

                m_inSynchronizedContext = wasInSynchronizedContext;
            }
        }

        public void visitSynchronizedStatement(PsiSynchronizedStatement psiSynchronizedStatement) {
            final boolean wasInSynchronizedContext = m_inSynchronizedContext;
            m_inSynchronizedContext = true;
            super.visitSynchronizedStatement(psiSynchronizedStatement);
            m_inSynchronizedContext = wasInSynchronizedContext;
        }
    }

}
