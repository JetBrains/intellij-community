package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class WaitWhileHoldingTwoLocksInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "'wait()' while holding two locks";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to '#ref()' is made while holding two locks #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new WaitWhileHoldingTwoLocksVisitor(this, inspectionManager, onTheFly);
    }

    private static class WaitWhileHoldingTwoLocksVisitor extends BaseInspectionVisitor {
        private int m_numLocksHeld = 0;

        private WaitWhileHoldingTwoLocksVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (m_numLocksHeld < 2) {
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
            if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                m_numLocksHeld++;
            }
            super.visitMethod(method);
            if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                m_numLocksHeld--;
            }
        }

        public void visitSynchronizedStatement(PsiSynchronizedStatement psiSynchronizedStatement) {
            m_numLocksHeld++;
            super.visitSynchronizedStatement(psiSynchronizedStatement);
            m_numLocksHeld--;
        }
    }

}
