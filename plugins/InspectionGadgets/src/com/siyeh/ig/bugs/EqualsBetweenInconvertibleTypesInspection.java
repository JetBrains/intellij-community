package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class EqualsBetweenInconvertibleTypesInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "'equals()' between objects of inconvertible types";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiReferenceExpression methodExpression = (PsiReferenceExpression) location.getParent();
        final PsiMethodCallExpression expression =
                (PsiMethodCallExpression) methodExpression.getParent();
        final PsiExpressionList argumentList = expression.getArgumentList();

        assert argumentList != null;
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiType comparedType = args[0].getType();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        final PsiType comparisonType = qualifier.getType();

        return "#ref() between objects of inconvertible types "
                + comparisonType.getPresentableText() + " and "
                + comparedType.getPresentableText() + " #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EqualsBetweenInconvertibleTypesVisitor();
    }

    private static class EqualsBetweenInconvertibleTypesVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"equals".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if (args == null) {
                return;
            }
            if (args.length != 1) {
                return;
            }
            final PsiExpression arg = args[0];
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (!areIncompatibleTypes(arg, qualifier)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean areIncompatibleTypes(PsiExpression exp1, PsiExpression exp2) {
            if (exp1 == null) {
                return false;
            }
            final PsiType comparedType = exp1.getType();
            if (comparedType == null) {
                return false;
            }
            if (exp2 == null) {
                return false;
            }
            final PsiType comparisonType = exp2.getType();
            if (comparisonType == null) {
                return false;
            }
            return !TypeConversionUtil.areTypesConvertible(comparedType,
                                                           comparisonType);
        }
    }

}
