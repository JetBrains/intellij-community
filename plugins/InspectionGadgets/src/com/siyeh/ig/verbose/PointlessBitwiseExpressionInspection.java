package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.IsConstantExpressionVisitor;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.*;

public class PointlessBitwiseExpressionInspection extends ExpressionInspection {
    private final PointlessBitwiseFix fix = new PointlessBitwiseFix();


    public String getDisplayName() {
        return "Pointless bitwise expression";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref can be replaced with " +
                calculateReplacementExpression((PsiExpression) location) + " #loc";
    }

    private static String calculateReplacementExpression(PsiExpression expression) {
        final PsiBinaryExpression exp = (PsiBinaryExpression) expression;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiJavaToken sign = exp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        final PsiType expressionType = exp.getType();
        if (tokenType.equals(JavaTokenType.AND)) {
            if (isZero(lhs, expressionType) || isAllOnes(rhs, expressionType)) {
                return lhs.getText();
            } else {
                return rhs.getText();
            }
        } else if (tokenType.equals(JavaTokenType.OR)) {
            if (isZero(lhs, expressionType) || isAllOnes(rhs, expressionType)) {
                return rhs.getText();
            } else {
                return lhs.getText();
            }
        } else if (tokenType.equals(JavaTokenType.XOR)) {
            if (isAllOnes(lhs, expressionType)) {
                return '~' + rhs.getText();
            } else if (isAllOnes(rhs, expressionType)) {
                return '~' + lhs.getText();
            } else if (isZero(rhs, expressionType)) {
                return lhs.getText();
            } else {
                return rhs.getText();
            }
        } else if (tokenType.equals(JavaTokenType.LTLT) ||
                tokenType.equals(JavaTokenType.GTGT) ||
                tokenType.equals(JavaTokenType.GTGTGT)) {
            return lhs.getText();
        } else {
            return "";
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new PointlessBitwiseVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class PointlessBitwiseFix extends InspectionGadgetsFix {
        public String getName() {
            return "Simplify";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiExpression expression = (PsiExpression) descriptor.getPsiElement();
            final String newExpression = calculateReplacementExpression(expression);
            replaceExpression(project, expression, newExpression);
        }

    }

    private static class PointlessBitwiseVisitor extends BaseInspectionVisitor {
        private PointlessBitwiseVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiType expressionType = expression.getType();
            if (expressionType == null) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if (rhs == null) {
                return;
            }

            final PsiType rhsType = rhs.getType();
            if (rhsType == null) {
                return;
            }
            if (rhsType.equals(PsiType.BOOLEAN) ||
                    "java.lang.Boolean".equals(rhsType.getCanonicalText())) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (lhs == null) {
                return;
            }
            final PsiType lhsType = lhs.getType();
            if (lhsType == null) {
                return;
            }
            if (lhsType.equals(PsiType.BOOLEAN) ||
                    "java.lang.Boolean".equals(lhsType.getCanonicalText())) {
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            final boolean isPointless;
            if (tokenType.equals(JavaTokenType.AND)) {
                isPointless = andExpressionIsPointless(lhs, rhs, expressionType);
            } else if (tokenType.equals(JavaTokenType.OR)) {
                isPointless = orExpressionIsPointless(lhs, rhs, expressionType);
            } else if (tokenType.equals(JavaTokenType.XOR)) {
                isPointless = xorExpressionIsPointless(lhs, rhs, expressionType);
            } else if (tokenType.equals(JavaTokenType.LTLT) ||
                    tokenType.equals(JavaTokenType.GTGT) ||
                    tokenType.equals(JavaTokenType.GTGTGT)) {
                isPointless = shiftExpressionIsPointless(rhs, expressionType);
            } else {
                isPointless = false;
            }
            if (!isPointless) {
                return;
            }
            registerError(expression);
        }
    }

    private static boolean shiftExpressionIsPointless(PsiExpression rhs, PsiType expressionType) {
        return isZero(rhs, expressionType);
    }

    private static boolean orExpressionIsPointless(PsiExpression lhs, PsiExpression rhs, PsiType expressionType) {
        return isZero(lhs, expressionType) || isZero(rhs, expressionType) || isAllOnes(lhs, expressionType) || isAllOnes(rhs, expressionType);
    }

    private static boolean xorExpressionIsPointless(PsiExpression lhs, PsiExpression rhs, PsiType expressionType) {
        return isZero(lhs, expressionType) || isZero(rhs, expressionType) || isAllOnes(lhs, expressionType) || isAllOnes(rhs, expressionType);
    }

    private static boolean andExpressionIsPointless(PsiExpression lhs, PsiExpression rhs, PsiType expressionType) {
        return isZero(lhs, expressionType) || isZero(rhs, expressionType) || isAllOnes(lhs, expressionType) || isAllOnes(rhs, expressionType);
    }

    private static boolean isZero(PsiExpression expression, PsiType expressionType) {
        final IsConstantExpressionVisitor visitor =
                new IsConstantExpressionVisitor();
        expression.accept(visitor);
        if (!visitor.isConstant()) {
            return false;
        }
        final Object value =
                ConstantExpressionUtil.computeCastTo(expression, expressionType);
        if (value == null) {
            return false;
        }
        if (value instanceof Integer && ((Integer) value).intValue() == 0) {
            return true;
        }
        if (value instanceof Long && ((Long) value).longValue() == 0L) {
            return true;
        }
        if (value instanceof Short && ((Short) value).shortValue() == 0) {
            return true;
        }
        if (value instanceof Character && ((Character) value).charValue() == 0) {
            return true;
        }
        if (value instanceof Byte && ((Byte) value).byteValue() == 0) {
            return true;
        }
        return false;
    }

    private static boolean isAllOnes(PsiExpression expression, PsiType expressionType) {
        final IsConstantExpressionVisitor visitor =
                new IsConstantExpressionVisitor();
        expression.accept(visitor);
        if (!visitor.isConstant()) {
            return false;
        }
        final Object value =
                ConstantExpressionUtil.computeCastTo(expression, expressionType);
        if (value == null) {
            return false;
        }
        if (value instanceof Integer && ((Integer) value).intValue() == 0xffffffff) {
            return true;
        }
        if (value instanceof Long && ((Long) value).longValue() == 0xffffffffffffffffL) {
            return true;
        }
        if (value instanceof Short && ((Short) value).shortValue() == (short) 0xffff) {
            return true;
        }
        if (value instanceof Character && ((Character) value).charValue() == (char) 0xffff) {
            return true;
        }
        if (value instanceof Byte && ((Byte) value).byteValue() == (byte) 0xff) {
            return true;
        }
        return false;
    }

}
