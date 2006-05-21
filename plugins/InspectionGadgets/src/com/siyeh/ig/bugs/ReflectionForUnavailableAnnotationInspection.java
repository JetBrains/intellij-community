package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class ReflectionForUnavailableAnnotationInspection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "reflection.for.unavailable.annotation.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "reflection.for.unavailable.annotation.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ReflectionForUnavailableAnnotationVisitor();
    }

    private static class ReflectionForUnavailableAnnotationVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!"isAnnotationPresent".equals(methodName) && !"getAnnotation".equals(methodName)) {
                return;
            }

            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 1) {
                return;
            }
            final PsiExpression arg = args[0];
            if (arg == null) {
                return;
            }
            if (!(arg instanceof PsiClassObjectAccessExpression)) {
                return;
            }
            final PsiMethod calledMethod = expression.resolveMethod();
            if (calledMethod == null) {
                return;
            }
            final PsiClass containingClass = calledMethod.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!"java.lang.Class".equals(containingClass.getQualifiedName())) {
                return;
            }
            final PsiTypeElement operand = ((PsiClassObjectAccessExpression) arg).getOperand();

            final PsiClassType annotationClassType = (PsiClassType) operand.getType();
            final PsiClass annotationClass = annotationClassType.resolve();
            if (annotationClass == null) {
                return;
            }

            final PsiAnnotation retentionAnnotation =
                    annotationClass.getModifierList().findAnnotation("java.lang.annotation.Retention");
            if (retentionAnnotation == null) {
                registerError(arg);
                return;
            }
            final PsiAnnotationParameterList parameters = retentionAnnotation.getParameterList();
            final PsiNameValuePair[] attributes = parameters.getAttributes();
            for (PsiNameValuePair attribute : attributes) {
                final String name = attribute.getName();
                if (name == null ||"value".equals(name)) {
                    final PsiAnnotationMemberValue value = attribute.getValue();
                    final String text = value.getText();
                    if (!text.contains("RUNTIME")) {
                        registerError(arg);
                        return;
                    }
                }
            }

        }
    }
}
