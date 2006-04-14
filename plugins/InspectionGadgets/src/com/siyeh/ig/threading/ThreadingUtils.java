package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ClassUtils;

public class ThreadingUtils {
    private ThreadingUtils() {
        super();
    }

    public static boolean isWaitCall(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if (!"wait".equals(methodName)) {
            return false;
        }
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
            return false;
        }
        final PsiParameterList paramList = method.getParameterList();
        final PsiParameter[] parameters = paramList.getParameters();
        final int numParams = parameters.length;
        if (numParams > 2) {
            return false;
        }
        if (numParams > 0) {
            final PsiType parameterType = parameters[0].getType();
            if (!parameterType.equals(PsiType.LONG)) {
                return false;
            }
        }

        if (numParams > 1) {
            final PsiType parameterType = parameters[1].getType();
            if (!parameterType.equals(PsiType.INT)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotifyOrNotifyAllCall(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();

        final String methodName = methodExpression.getReferenceName();
        if (!"notify".equals(methodName) && !"notifyAll".equals(methodName)) {
            return false;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();

        final PsiExpression[] args = argumentList.getExpressions();
        return args.length == 0;
    }

    public static boolean isSignalOrSignalAllCall(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if (!"signal".equals(methodName) && !"signalAll".equals(methodName)) {
            return false;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        if (args.length != 0) {
            return false;
        }
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
            return false;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        return ClassUtils.isSubclass(containingClass,
                        "java.util.concurrent.locks.Condition");

    }
    public static boolean isAwaitCall(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if (!"await".equals(methodName)
                && !"awaitUntil".equals(methodName)
                && !"awaitUninterruptibly".equals(methodName)
                && !"awaitNanos".equals(methodName)) {
            return false;
        }
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
            return false;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        return ClassUtils.isSubclass(containingClass,
                        "java.util.concurrent.locks.Condition");

    }
}
