package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;

public class SubtractionInCompareToInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Subtraction in compareTo()";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Subtraction (#ref) in compareTo() may result in overflow errors #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SubtractionInCompareToVisitor(this, inspectionManager, onTheFly);
    }

    private static class SubtractionInCompareToVisitor extends BaseInspectionVisitor {
        private SubtractionInCompareToVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression exp) {
            super.visitBinaryExpression(exp);
            if(!WellFormednessUtils.isWellFormed(exp)){
                return;
            }
            if (!isSubtraction(exp)) {
                return;
            }
            final PsiMethod method =
                    (PsiMethod) PsiTreeUtil.getParentOfType(exp, PsiMethod.class);
            if (!isCompareTo(method)) {
                return;
            }
            registerError(exp);
        }

        private static boolean isCompareTo(PsiMethod method) {
            if (method == null) {
                return false;
            }
            final String methodName = method.getName();
            if (!"compareTo".equals(methodName)) {
                return false;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return false;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length != 1) {
                return false;
            }
            final PsiType returnType = method.getReturnType();
            return TypeUtils.typeEquals("int", returnType);
        }

        private static boolean isSubtraction(PsiBinaryExpression exp) {
            final PsiExpression lhs = exp.getLOperand();
            if (lhs == null) {
                return false;
            }
            final PsiExpression rhs = exp.getROperand();
            if (rhs == null) {
                return false;
            }
            final PsiJavaToken sign = exp.getOperationSign();
            if (sign == null) {
                return false;
            }
            final IElementType tokenType = sign.getTokenType();
            return tokenType.equals(JavaTokenType.MINUS);
        }

    }

}
