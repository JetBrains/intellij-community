package com.siyeh.ig.bugs;

import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ReplaceAllDotInspection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "replace.all.dot.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "replace.all.dot.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SystemSetSecurityManagerVisitor();
    }

    private static class SystemSetSecurityManagerVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName = methodExpression.getReferenceName();
            if (!"replaceAll".equals(methodName)) {
                return;
            }
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            if (args.length != 2) {
                return;
            }
            final PsiExpression arg = args[0];
            if (!PsiUtil.isConstantExpression(arg)) {
                return;
            }

            final PsiType argType = arg.getType();
            if (argType == null) {
                return;
            }
            if (!"java.lang.String".equals(argType.getCanonicalText())) {
                return;
            }
            final String argValue = (String) ConstantExpressionUtil.computeCastTo(arg, argType);
            if (!".".equals(argValue)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!"java.lang.String".equals(containingClass.getQualifiedName())) {
                return;
            }

            registerMethodCallError(expression);
        }
    }
}
