package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

import java.util.HashMap;
import java.util.Map;

public class ParenthesesUtils {
    private ParenthesesUtils() {
        super();

    }

    private static final int PARENTHESIZED_PRECEDENCE = 0;
    private static final int LITERAL_PRECEDENCE = 0;
    public static final int METHOD_CALL_PRECEDENCE = 1;

    private static final int POSTFIX_PRECEDENCE = 2;
    public static final int PREFIX_PRECEDENCE = 3;
    public static final int TYPE_CAST_PRECEDENCE = 4;
    public static final int MULTIPLICATIVE_PRECEDENCE = 5;
    private static final int ADDITIVE_PRECEDENCE = 6;
    public static final int SHIFT_PRECEDENCE = 7;
    private static final int RELATIONAL_PRECEDENCE = 8;
    private static final int EQUALITY_PRECEDENCE = 9;

    private static final int BINARY_AND_PRECEDENCE = 10;
    private static final int BINARY_XOR_PRECEDENCE = 11;
    private static final int BINARY_OR_PRECEDENCE = 12;
    private static final int AND_PRECEDENCE = 13;
    private static final int OR_PRECEDENCE = 14;
    private static final int CONDITIONAL_PRECEDENCE = 15;
    private static final int ASSIGNMENT_PRECEDENCE = 16;

    private static final int NUM_PRECEDENCES = 17;

    private static final Map s_binaryOperatorPrecedence = new HashMap(NUM_PRECEDENCES);

    static {
        s_binaryOperatorPrecedence.put("+", new Integer(ADDITIVE_PRECEDENCE));
        s_binaryOperatorPrecedence.put("-", new Integer(ADDITIVE_PRECEDENCE));
        s_binaryOperatorPrecedence.put("*", new Integer(MULTIPLICATIVE_PRECEDENCE));
        s_binaryOperatorPrecedence.put("/", new Integer(MULTIPLICATIVE_PRECEDENCE));
        s_binaryOperatorPrecedence.put("%", new Integer(MULTIPLICATIVE_PRECEDENCE));
        s_binaryOperatorPrecedence.put("&&", new Integer(AND_PRECEDENCE));
        s_binaryOperatorPrecedence.put("||", new Integer(OR_PRECEDENCE));
        s_binaryOperatorPrecedence.put("&", new Integer(BINARY_AND_PRECEDENCE));
        s_binaryOperatorPrecedence.put("|", new Integer(BINARY_OR_PRECEDENCE));
        s_binaryOperatorPrecedence.put("^", new Integer(BINARY_XOR_PRECEDENCE));
        s_binaryOperatorPrecedence.put("<<", new Integer(SHIFT_PRECEDENCE));
        s_binaryOperatorPrecedence.put(">>", new Integer(SHIFT_PRECEDENCE));
        s_binaryOperatorPrecedence.put(">>>", new Integer(SHIFT_PRECEDENCE));
        s_binaryOperatorPrecedence.put(">", new Integer(RELATIONAL_PRECEDENCE));
        s_binaryOperatorPrecedence.put(">=", new Integer(RELATIONAL_PRECEDENCE));
        s_binaryOperatorPrecedence.put("<", new Integer(RELATIONAL_PRECEDENCE));
        s_binaryOperatorPrecedence.put("<=", new Integer(RELATIONAL_PRECEDENCE));
        s_binaryOperatorPrecedence.put("==", new Integer(EQUALITY_PRECEDENCE));
        s_binaryOperatorPrecedence.put("!=", new Integer(EQUALITY_PRECEDENCE));
    }

    public static PsiExpression stripParentheses(PsiExpression exp) {
        PsiExpression parenthesized = exp;
        while (parenthesized instanceof PsiParenthesizedExpression) {
            parenthesized = ((PsiParenthesizedExpression) parenthesized).getExpression();
        }
        return parenthesized;
    }

    public static int getPrecendence(PsiExpression exp) {
        if (exp instanceof PsiThisExpression ||
                exp instanceof PsiLiteralExpression ||
                exp instanceof PsiSuperExpression ||
                exp instanceof PsiReferenceExpression ||
                exp instanceof PsiClassObjectAccessExpression ||
                exp instanceof PsiArrayAccessExpression ||
                exp instanceof PsiArrayInitializerExpression) {
            return LITERAL_PRECEDENCE;
        }
        if (exp instanceof PsiMethodCallExpression) {
            return METHOD_CALL_PRECEDENCE;
        }
        if (exp instanceof PsiTypeCastExpression ||
                exp instanceof PsiNewExpression) {
            return TYPE_CAST_PRECEDENCE;
        }
        if (exp instanceof PsiPrefixExpression) {
            return PREFIX_PRECEDENCE;
        }
        if (exp instanceof PsiPostfixExpression) {
            return POSTFIX_PRECEDENCE;
        }
        if (exp instanceof PsiBinaryExpression) {
            final PsiJavaToken sign = ((PsiBinaryExpression) exp).getOperationSign();
            return precedenceForBinaryOperator(sign);
        }
        if (exp instanceof PsiInstanceOfExpression) {
            return RELATIONAL_PRECEDENCE;
        }
        if (exp instanceof PsiConditionalExpression) {
            return CONDITIONAL_PRECEDENCE;
        }
        if (exp instanceof PsiAssignmentExpression) {
            return ASSIGNMENT_PRECEDENCE;
        }
        if (exp instanceof PsiParenthesizedExpression) {
            return PARENTHESIZED_PRECEDENCE;
        }
        return -1;
    }

    private static int precedenceForBinaryOperator(PsiJavaToken sign) {
        final String operator = sign.getText();
        final Integer precedence = (Integer) s_binaryOperatorPrecedence.get(operator);
        return precedence.intValue();
    }

    public static String removeParentheses(PsiExpression exp) {
        if (exp instanceof PsiMethodCallExpression) {
            return removeParensFromMethodCallExpression((PsiMethodCallExpression) exp);
        }
        if (exp instanceof PsiNewExpression) {
            return removeParensFromNewExpression((PsiNewExpression) exp);
        }
        if (exp instanceof PsiAssignmentExpression) {
            return removeParensFromAssignmentExpression((PsiAssignmentExpression) exp);
        }
        if (exp instanceof PsiArrayInitializerExpression) {
            return removeParensFromArrayInitializerExpression((PsiArrayInitializerExpression) exp);
        }
        if (exp instanceof PsiTypeCastExpression) {
            return removeParensFromTypeCastExpression((PsiTypeCastExpression) exp);
        }
        if (exp instanceof PsiArrayAccessExpression) {
            return removeParensFromArrayAccessExpression((PsiArrayAccessExpression) exp);
        }
        if (exp instanceof PsiPrefixExpression) {
            return removeParensFromPrefixExpression((PsiPrefixExpression) exp);
        }
        if (exp instanceof PsiPostfixExpression) {
            return removeParensFromPostfixExpression((PsiPostfixExpression) exp);
        }
        if (exp instanceof PsiBinaryExpression) {
            return removeParensFromBinaryExpression((PsiBinaryExpression) exp);
        }
        if (exp instanceof PsiInstanceOfExpression) {
            return removeParensFromInstanceOfExpression((PsiInstanceOfExpression) exp);
        }
        if (exp instanceof PsiConditionalExpression) {
            return removeParensFromConditionalExpression((PsiConditionalExpression) exp);
        }
        if (exp instanceof PsiParenthesizedExpression) {
            return removeParensFromParenthesizedExpression((PsiParenthesizedExpression) exp);
        }

        return exp.getText();
    }

    private static String removeParensFromParenthesizedExpression(PsiParenthesizedExpression parenthesizedExp) {
        PsiExpression body = parenthesizedExp.getExpression();
        while (body instanceof PsiParenthesizedExpression) {
            body = ((PsiParenthesizedExpression) body).getExpression();
        }
        if (!(parenthesizedExp.getParent() instanceof PsiExpression)) {
            return removeParentheses(body);
        }
        final PsiExpression parentExp = (PsiExpression) parenthesizedExp.getParent();
        final int parentPrecedence = getPrecendence(parentExp);
        final int childPrecedence = getPrecendence(body);
        if (parentPrecedence < childPrecedence) {
            return '(' + removeParentheses(body) + ')';
        } else if (parentPrecedence == childPrecedence) {
            if (parentExp instanceof PsiBinaryExpression &&
                    body instanceof PsiBinaryExpression) {
                final IElementType parentOperator =
                        ((PsiBinaryExpression) parentExp).getOperationSign().getTokenType();
                final IElementType bodyOperator =
                        ((PsiBinaryExpression) body).getOperationSign().getTokenType();

                final PsiExpression lhs = ((PsiBinaryExpression) parentExp).getLOperand();

                if (lhs.equals(parenthesizedExp) && parentOperator.equals(bodyOperator)) {
                    return removeParentheses(body);
                } else {
                    return '(' + removeParentheses(body) + ')';
                }
            } else {
                return removeParentheses(body);
            }
        } else {
            return removeParentheses(body);
        }
    }

    private static String removeParensFromConditionalExpression(PsiConditionalExpression conditionalExp) {
        final PsiExpression condition = conditionalExp.getCondition();
        final PsiExpression thenBranch = conditionalExp.getThenExpression();
        final PsiExpression elseBranch = conditionalExp.getElseExpression();
        return removeParentheses(condition) + '?' +
                removeParentheses(thenBranch) + ':' +
                removeParentheses(elseBranch);
    }

    private static String removeParensFromInstanceOfExpression(PsiInstanceOfExpression instanceofExp) {
        final PsiExpression body = instanceofExp.getOperand();
        final PsiTypeElement type = instanceofExp.getCheckType();
        return removeParentheses(body) + " instanceof " + type.getText();
    }

    private static String removeParensFromBinaryExpression(PsiBinaryExpression binaryExp) {
        final PsiExpression lhs = binaryExp.getLOperand();
        final PsiExpression rhs = binaryExp.getROperand();
        final PsiJavaToken sign = binaryExp.getOperationSign();
        return removeParentheses(lhs) + sign.getText() + removeParentheses(rhs);
    }

    private static String removeParensFromPostfixExpression(PsiPostfixExpression postfixExp) {
        final PsiExpression body = postfixExp.getOperand();
        final PsiJavaToken sign = postfixExp.getOperationSign();
        final String operand = sign.getText();
        return removeParentheses(body) + operand;
    }

    private static String removeParensFromPrefixExpression(PsiPrefixExpression prefixExp) {
        final PsiExpression body = prefixExp.getOperand();
        final PsiJavaToken sign = prefixExp.getOperationSign();
        final String operand = sign.getText();
        return operand + removeParentheses(body);
    }

    private static String removeParensFromArrayAccessExpression(PsiArrayAccessExpression arrayAccessExp) {
        final PsiExpression arrayExp = arrayAccessExp.getArrayExpression();
        final PsiExpression indexExp = arrayAccessExp.getIndexExpression();
        return removeParentheses(arrayExp) + '[' + removeParentheses(indexExp) + ']';
    }

    private static String removeParensFromTypeCastExpression(PsiTypeCastExpression typeCast) {
        final PsiExpression body = typeCast.getOperand();
        final PsiTypeElement type = typeCast.getCastType();
        return '(' + type.getText() + ')' + removeParentheses(body);
    }

    private static String removeParensFromArrayInitializerExpression(PsiArrayInitializerExpression init) {
        final PsiExpression[] contents = init.getInitializers();
        final String text = init.getText();
        final int textLength = text.length();
        final StringBuffer out = new StringBuffer(textLength);
        out.append('{');
        for (int i = 0; i < contents.length; i++) {
            final PsiExpression arg = contents[i];
            if (i != 0) {
                out.append(',');
            }
            final String strippedArg = removeParentheses(arg);
            out.append(strippedArg);
        }
        out.append('}');
        return out.toString();
    }

    private static String removeParensFromAssignmentExpression(PsiAssignmentExpression assignment) {
        final PsiExpression lhs = assignment.getLExpression();
        final PsiExpression rhs = assignment.getRExpression();
        final PsiJavaToken sign = assignment.getOperationSign();
        return removeParentheses(lhs) + sign.getText() +
                removeParentheses(rhs);
    }

    private static String removeParensFromNewExpression(PsiNewExpression newExp) {
        final PsiExpression[] dimensions = newExp.getArrayDimensions();
        String[] strippedDimensions = null;
        if (dimensions != null) {
            strippedDimensions = new String[dimensions.length];
            for (int i = 0; i < dimensions.length; i++) {
                strippedDimensions[i] = removeParentheses(dimensions[i]);
            }
        }

        final PsiExpression qualifier = newExp.getQualifier();
        final PsiExpression arrayInitializer = newExp.getArrayInitializer();
        String strippedInitializer = null;
        if (arrayInitializer != null) {
            strippedInitializer = removeParentheses(arrayInitializer);
        }

        final PsiExpressionList argumentList = newExp.getArgumentList();
        String[] strippedArgs = null;
        if (argumentList != null) {
            final PsiExpression[] args = argumentList.getExpressions();
            if (args != null) {
                strippedArgs = new String[args.length];
                for (int i = 0; i < args.length; i++) {
                    strippedArgs[i] = removeParentheses(args[i]);
                }
            }
        }
        final String expressionText = newExp.getText();
        if (qualifier != null) {
            return expressionText;
        }
        final PsiElement[] children = newExp.getChildren();
        for (int i = 0; i < children.length; i++) {
            final PsiElement child = children[i];
            if (child instanceof PsiAnonymousClass) {
                return expressionText;
            }
        }
        final int length = expressionText.length();
        final StringBuffer out = new StringBuffer(length);
        out.append("new ");
        final PsiType type = newExp.getType();
        final PsiType deepType = type.getDeepComponentType();
        final String text = deepType.getPresentableText();
        out.append(text);
        if (strippedArgs != null) {
            out.append('(');
            for (int i = 0; i < strippedArgs.length; i++) {
                if (i != 0) {
                    out.append(',');
                }
                out.append(strippedArgs[i]);
            }
            out.append(')');
        }

        if (strippedDimensions != null) {
            if (strippedDimensions.length > 0) {
                for (int i = 0; i < strippedDimensions.length; i++) {
                    out.append('[');
                    out.append(strippedDimensions[i]);
                    out.append(']');
                }
            } else {
                final int dimensionCount = type.getArrayDimensions();
                for (int i = 0; i < dimensionCount; i++) {
                    out.append("[]");
                }
            }
        }
        if (strippedInitializer != null) {
            out.append(strippedInitializer);
        }
        return out.toString();
    }

    private static String removeParensFromMethodCallExpression(PsiMethodCallExpression methCall) {
        final PsiReferenceExpression target = methCall.getMethodExpression();
        final PsiExpressionList argumentList = methCall.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();

        final String methodCallText = methCall.getText();
        final int length = methodCallText.length();
        final StringBuffer out = new StringBuffer(length);
        final String strippedTarget = removeParentheses(target);
        out.append(strippedTarget);
        out.append('(');
        for (int i = 0; i < args.length; i++) {
            final PsiExpression arg = args[i];
            if (i != 0) {
                out.append(',');
            }
            final String strippedArg = removeParentheses(arg);
            out.append(strippedArg);
        }
        out.append(')');
        return out.toString();
    }

}
