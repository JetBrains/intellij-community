package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class ImplicitNumericConversionInspection extends ExpressionInspection {
    /** @noinspection StaticCollection*/
    private static final Map s_typePrecisions = new HashMap(7);

    static {
        s_typePrecisions.put("byte", new Integer(1));
        s_typePrecisions.put("char", new Integer(2));
        s_typePrecisions.put("short", new Integer(2));
        s_typePrecisions.put("int", new Integer(3));
        s_typePrecisions.put("long", new Integer(4));
        s_typePrecisions.put("float", new Integer(5));
        s_typePrecisions.put("double", new Integer(6));
    }

    /** @noinspection PublicField*/
    public boolean m_ignoreWideningConversions = false;

    public String getDisplayName() {
        return "Implicit numeric conversion";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore widening conversions",
                this, "m_ignoreWideningConversions");
    }

    public String buildErrorString(PsiElement location) {
        final PsiExpression expression = (PsiExpression) location;
        final PsiType type = expression.getType();
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression);
        return "Implicit numeric conversion of #ref from " + type.getPresentableText() + " to " +
                expectedType.getPresentableText() +
                " #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ImplicitNumericConversionVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new ImplicitNumericConversionFix(location);
    }

    private static class ImplicitNumericConversionFix extends InspectionGadgetsFix {
        private final String m_name;

        private ImplicitNumericConversionFix(PsiElement field) {
            super();
            final PsiExpression expression = (PsiExpression) field;
            final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression);
            if (isConvertible(expression, expectedType)) {
                m_name = "Convert to " + expectedType.getCanonicalText() + " literal";
            } else {
                m_name = "Make conversion explicit";
            }
        }

        public String getName() {
            return m_name;
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiExpression expression = (PsiExpression) descriptor.getPsiElement();
            final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression);
            if (isConvertible(expression, expectedType)) {
                final String newExpression = convertExpression(expression, expectedType);
                replaceExpression(project, expression, newExpression);
            } else {
                final String newExpression;
                if (ParenthesesUtils.getPrecendence(expression) <= ParenthesesUtils.TYPE_CAST_PRECEDENCE) {
                    newExpression = '(' + expectedType.getPresentableText() + ')' + expression.getText();
                } else {
                    newExpression = '(' + expectedType.getPresentableText() + ")(" + expression.getText() + ')';
                }
                replaceExpression(project, expression, newExpression);
            }
        }

        private static String convertExpression(PsiExpression expression, PsiType expectedType) {
            final PsiType expressionType = expression.getType();

            if (expressionType.equals(PsiType.INT) && expectedType.equals(PsiType.LONG)) {
                return expression.getText() + 'L';
            }
            if (expressionType.equals(PsiType.INT) && expectedType.equals(PsiType.FLOAT)) {
                return expression.getText() + ".0F";
            }
            if (expressionType.equals(PsiType.INT) && expectedType.equals(PsiType.DOUBLE)) {
                return expression.getText() + ".0";
            }
            if (expressionType.equals(PsiType.LONG) && expectedType.equals(PsiType.FLOAT)) {
                final String text = expression.getText();
                final int length = text.length();
                return text.substring(0, length - 1) + ".0F";
            }
            if (expressionType.equals(PsiType.LONG) && expectedType.equals(PsiType.DOUBLE)) {
                final String text = expression.getText();
                final int length = text.length();
                return text.substring(0, length - 1) + ".0";
            }
            if (expressionType.equals(PsiType.DOUBLE) && expectedType.equals(PsiType.FLOAT)) {
                final String text = expression.getText();
                final int length = text.length();
                if (text.charAt(length - 1) == 'd' || text.charAt(length - 1) == 'D') {
                    return text.substring(0, length - 1) + 'F';
                } else {
                    return text + 'F';
                }
            }
            if (expressionType.equals(PsiType.FLOAT) && expectedType.equals(PsiType.DOUBLE)) {
                final String text = expression.getText();
                final int length = text.length();
                return text.substring(0, length - 1);
            }
            return null;   //can't happen
        }

        private static boolean isConvertible(PsiExpression expression, PsiType expectedType) {
            if (!(expression instanceof PsiLiteralExpression) && !isNegatedLiteral(expression)) {
                return false;
            }
            final PsiType expressionType = expression.getType();
            if (expressionType == null) {
                return false;
            }
            if(hasLowerPrecision(expectedType,  expressionType))
            {
                return false;
            }
            if (isIntegral(expressionType) && isIntegral(expectedType)) {
                return true;
            }
            if (isIntegral(expressionType) && isFloatingPoint(expectedType)) {
                return true;
            }
            return isFloatingPoint(expressionType) &&
                           isFloatingPoint(expectedType);

        }

        private static boolean isNegatedLiteral(PsiExpression expression) {
            if (!(expression instanceof PsiPrefixExpression))
            return false;
            final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
            final PsiJavaToken sign = prefixExpression.getOperationSign();
            if(sign == null)
            {
                return false;
            }
            final IElementType tokenType = sign.getTokenType();
            if(!JavaTokenType.MINUS.equals(tokenType))
            {
                return false;
            }
            final PsiExpression operand = prefixExpression.getOperand();
            return operand instanceof PsiLiteralExpression;
        }

        private static boolean isIntegral(PsiType expressionType) {
            return expressionType.equals(PsiType.INT) || expressionType.equals(PsiType.LONG);
        }

        private static boolean isFloatingPoint(PsiType expressionType) {
            return expressionType.equals(PsiType.FLOAT) || expressionType.equals(PsiType.DOUBLE);
        }
    }

    private class ImplicitNumericConversionVisitor extends BaseInspectionVisitor {
        private ImplicitNumericConversionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitExpression(PsiExpression exp) {
            super.visitExpression(exp);
            final PsiType expressionType = exp.getType();
            if (!ClassUtils.isPrimitiveNumericType(expressionType)) {
                return;
            }
            final PsiElement parent = exp.getParent();
            if(parent != null && parent instanceof PsiParenthesizedExpression){
                return;
            }
            final PsiType expectedType = ExpectedTypeUtils.findExpectedType(exp);
            if (!ClassUtils.isPrimitiveNumericType(expectedType)) {
                return;
            }
            if (expectedType.equals(expressionType)) {
                return;
            }
            if (m_ignoreWideningConversions && hasLowerPrecision(expressionType, expectedType)) {
                return;
            }
            registerError(exp);
        }
    }


    private static boolean hasLowerPrecision(PsiType expressionType, PsiType expectedType) {
        final String operandTypeText = expressionType.getCanonicalText();
        final Integer operandPrecision = (Integer) s_typePrecisions.get(operandTypeText);
        final String castTypeText = expectedType.getCanonicalText();
        final Integer castPrecision = (Integer) s_typePrecisions.get(castTypeText);
        return operandPrecision.intValue() <= castPrecision.intValue();
    }

}
