package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

public class StringConcatenationInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "String concatenation";
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "String concatenation (#ref) in an internationalized context #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StringConcatenationVisitor(this, inspectionManager, onTheFly);
    }

    private static class StringConcatenationVisitor extends BaseInspectionVisitor {
        private StringConcatenationVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression))
            {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            if(sign == null)
            {
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            if (!JavaTokenType.PLUS.equals(tokenType)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();

            final PsiType lhsType = lhs.getType();
            final PsiExpression rhs = expression.getROperand();
            final PsiType rhsType = rhs.getType();
            if(!TypeUtils.isJavaLangString(lhsType) &&
                       !TypeUtils.isJavaLangString(rhsType)){
                return;
            }
            registerError(sign);

        }

    }

}
