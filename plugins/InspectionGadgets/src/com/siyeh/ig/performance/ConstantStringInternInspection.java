package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ConstantStringInternInspection extends ExpressionInspection {

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "constant.string.intern.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new ConstantStringInternFix();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConstantStringInternVisitor();
    }

    private static class ConstantStringInternFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message("constant.string.intern.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression)descriptor.getPsiElement().getParent().getParent();
            final PsiReferenceExpression expression =
                    call.getMethodExpression();
            final PsiExpression qualifier = expression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final String qualifierText = qualifier.getText();
            replaceExpression(call, qualifierText);
        }
    }

    private static class ConstantStringInternVisitor extends BaseInspectionVisitor {
        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!"intern".equals(methodName)) {
                return;
            }
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            if (args.length != 0) {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if(qualifier == null)
            {
                return;
            }
            if(!PsiUtil.isConstantExpression(qualifier))
            {
                return;
            }

            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String className = aClass.getQualifiedName();
            if (!"java.lang.String".equals(className)) {
                return;
            }
            registerMethodCallError(expression);
        }

    }
}
