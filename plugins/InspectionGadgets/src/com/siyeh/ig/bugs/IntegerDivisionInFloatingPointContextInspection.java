package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;

import java.util.Set;
import java.util.HashSet;

public class IntegerDivisionInFloatingPointContextInspection extends ExpressionInspection {
    private static final Set s_integralTypes = new HashSet(10);

    static {
        s_integralTypes.add("int");
        s_integralTypes.add("long");
        s_integralTypes.add("short");
        s_integralTypes.add("byte");
        s_integralTypes.add("char");
        s_integralTypes.add("java.lang.Integer");
        s_integralTypes.add("java.lang.Long");
        s_integralTypes.add("java.lang.Short");
        s_integralTypes.add("java.lang.Byte");
        s_integralTypes.add("java.lang.Char");
    }

    public String getDisplayName() {
        return "Integer division in floating point context";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref: integer division in floating-point context #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new FloatingPointEqualityComparisonVisitor(this, inspectionManager, onTheFly);
    }

    private static class FloatingPointEqualityComparisonVisitor extends BaseInspectionVisitor {

        private FloatingPointEqualityComparisonVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.DIV)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (lhs == null) {
                return;
            }
            final PsiType lhsType = lhs.getType();
            if (!isIntegral(lhsType)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if (rhs == null) {
                return;
            }
            final PsiType rhsType = rhs.getType();
            if (!isIntegral(rhsType)) {
                return;
            }
            final PsiExpression context = getContainingExpression(expression);
            if (context == null) {
                return;
            }
            final PsiType contextType = ExpectedTypeUtils.findExpectedType(context);
            if (contextType == null) {
                return;
            }
            if (!(contextType.equals(PsiType.FLOAT)
                    || contextType.equals(PsiType.DOUBLE))) {
                return;
            }
            registerError(expression);
        }


        private PsiExpression getContainingExpression(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (parent == null) {
                return expression;
            }
            if (parent instanceof PsiPrefixExpression ||
                    parent instanceof PsiPostfixExpression ||
                    parent instanceof PsiBinaryExpression ||
                    parent instanceof PsiParenthesizedExpression) {
                return getContainingExpression((PsiExpression) parent);
            }
            return expression;
        }
    }

    private static boolean isIntegral(PsiType type) {

        if (type == null) {
            return false;
        }
        final String text = type.getCanonicalText();
        if (text == null) {
            return false;
        }
        return s_integralTypes.contains(text);
    }

}
