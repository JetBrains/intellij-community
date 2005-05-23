package com.siyeh.ig.numeric;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class IntegerDivisionInFloatingPointContextInspection extends ExpressionInspection {
    /** @noinspection StaticCollection*/
    private static final Set<String> s_integralTypes = new HashSet<String>(10);

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
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref: integer division in floating-point context #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FloatingPointEqualityComparisonVisitor();
    }

    private static class FloatingPointEqualityComparisonVisitor extends BaseInspectionVisitor {

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.DIV)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiType lhsType = lhs.getType();
            if (!isIntegral(lhsType)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
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
