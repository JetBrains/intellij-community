package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;

public class WaitNotInSynchronizedContextInspection extends StatementInspection {

    public String getDisplayName() {
        return "'wait()' while not synced";
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

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (m_inSynchronizedContext) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"wait".equals(methodName)) {
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
            if (numParams > 2) {
                return;
            }
            if (numParams > 0) {
                final PsiType parameterType = parameters[0].getType();
                if (!parameterType.equals(PsiType.LONG)) {
                    return;
                }
            }

            if (numParams > 1) {
                final PsiType parameterType = parameters[1].getType();
                if (!parameterType.equals(PsiType.INT)) {
                    return;
                }
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
