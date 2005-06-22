package com.siyeh.ig.security;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class LoadLibraryWithNonConstantStringInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Call to 'System.loadLibrary()' with non-constant string";
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to System.#ref() with non-constant argument #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new RuntimeExecVisitor();
    }

    private static class RuntimeExecVisitor extends BaseInspectionVisitor {


        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"loadLibrary".equals(methodName)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null)
            {
                return;
            }
            if(!ClassUtils.isSubclass(aClass, "java.lang.System")) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null)
            {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if(args == null || args.length==0)
            {
                return;
            }
            final PsiExpression arg = args[0];
            final PsiType type = arg.getType();
            if(type == null)
            {
                return;
            }
            final String typeText = type.getCanonicalText();
            if(!"java.lang.String".equals(typeText))
            {
                return;
            }
            final String stringValue =
                    (String) ConstantExpressionUtil.computeCastTo(arg, type);
            if(stringValue!=null)
            {
                return;
            }
            registerMethodCallError(expression);
        }
    }

}
