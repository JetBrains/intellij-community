package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new WaitWhileHoldingTwoLocksVisitor();
    }

    private static class WaitWhileHoldingTwoLocksVisitor extends BaseInspectionVisitor {
        private int m_numLocksHeld = 0;

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
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

        public void visitMethod(@NotNull PsiMethod method) {
            if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                m_numLocksHeld++;
            }
            super.visitMethod(method);
            if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                m_numLocksHeld--;
            }
        }

        public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement psiSynchronizedStatement) {
            m_numLocksHeld++;
            super.visitSynchronizedStatement(psiSynchronizedStatement);
            m_numLocksHeld--;
        }
    }

}
