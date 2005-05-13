package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

public class NotifyNotInSynchronizedContextInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "'notify()' or 'notifyAll()' while not synced";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to #ref() is made outside of a synchronized context  #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new WaitNotInSynchronizedContextVisitor(this, inspectionManager, onTheFly);
    }

    private static class WaitNotInSynchronizedContextVisitor extends BaseInspectionVisitor {
        private boolean m_inSynchronizedContext = false;

        private WaitNotInSynchronizedContextVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (m_inSynchronizedContext) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"notify".equals(methodName) && !"notifyAll".equals(methodName)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            final int numParams = parameters.length;
            if (numParams != 0) {
                return;
            }
            registerMethodCallError(expression);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            final boolean wasInSynchronizedContext = m_inSynchronizedContext;
            if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {

                m_inSynchronizedContext = true;
            }
            super.visitMethod(method);
            if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {

                m_inSynchronizedContext = wasInSynchronizedContext;
            }
        }

        public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement psiSynchronizedStatement) {
            final boolean wasInSynchronizedContext = m_inSynchronizedContext;
            m_inSynchronizedContext = true;
            super.visitSynchronizedStatement(psiSynchronizedStatement);
            m_inSynchronizedContext = wasInSynchronizedContext;
        }
    }

}
