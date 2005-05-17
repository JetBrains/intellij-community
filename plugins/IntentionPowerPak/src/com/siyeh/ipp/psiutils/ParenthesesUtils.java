package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

import java.util.HashMap;
import java.util.Map;

public class ParenthesesUtils{
    private static final int PARENTHESIZED_EXPRESSION_PRECEDENCE = 0;
    private static final int LITERAL_PRECEDENCE = 0;
    public static final int METHOD_CALL_PRECEDENCE = 1;

    private static final int POSTFIX_PRECEDENCE = 2;
    public static final int PREFIX_PRECEDENCE = 3;
    private static final int TYPE_CAST_PRECEDENCE = 4;
    public static final int MULTIPLICATIVE_PRECEDENCE = 5;
    private static final int ADDITIVE_PRECEDENCE = 6;
    public static final int SHIFT_PRECEDENCE = 7;
    private static final int RELATIONAL_PRECEDENCE = 8;
    public static final int EQUALITY_PRECEDENCE = 9;

    private static final int BINARY_AND_PRECEDENCE = 10;
    private static final int BINARY_XOR_PRECEDENCE = 11;
    private static final int BINARY_OR_PRECEDENCE = 12;
    public static final int AND_PRECEDENCE = 13;
    public static final int OR_PRECEDENCE = 14;
    public static final int CONDITIONAL_EXPRESSION_EXPRESSION = 15;
    private static final int ASSIGNMENT_EXPRESSION_PRECEDENCE = 16;

    private static final Map<String, Integer> s_binaryOperatorPrecedence = new HashMap<String, Integer>(16);

    static {
        s_binaryOperatorPrecedence.put("+", ADDITIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("-", ADDITIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("*",
                                       MULTIPLICATIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("/",
                                       MULTIPLICATIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("%",
                                       MULTIPLICATIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("&&", AND_PRECEDENCE);
        s_binaryOperatorPrecedence.put("||", OR_PRECEDENCE);
        s_binaryOperatorPrecedence.put("&", BINARY_AND_PRECEDENCE);
        s_binaryOperatorPrecedence.put("|", BINARY_OR_PRECEDENCE);
        s_binaryOperatorPrecedence.put("^", BINARY_XOR_PRECEDENCE);
        s_binaryOperatorPrecedence.put("<<", SHIFT_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">>", SHIFT_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">>>", SHIFT_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">", RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">=",
                                       RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put("<", RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put("<=",
                                       RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put("==", EQUALITY_PRECEDENCE);
        s_binaryOperatorPrecedence.put("!=", EQUALITY_PRECEDENCE);
    }

    private ParenthesesUtils(){
        super();
    }

    public static PsiExpression stripParentheses(PsiExpression exp){
        PsiExpression parenthesized = exp;
        while(parenthesized instanceof PsiParenthesizedExpression){
            parenthesized = ((PsiParenthesizedExpression) parenthesized).getExpression();
        }
        return parenthesized;
    }

    public static int getPrecendence(PsiExpression exp){

        if(exp instanceof PsiThisExpression ||
                exp instanceof PsiLiteralExpression ||
                exp instanceof PsiSuperExpression ||
                exp instanceof PsiReferenceExpression ||
                exp instanceof PsiClassObjectAccessExpression ||
                exp instanceof PsiArrayAccessExpression ||
                exp instanceof PsiArrayInitializerExpression){
            return LITERAL_PRECEDENCE;
        }
        if(exp instanceof PsiMethodCallExpression){
            return METHOD_CALL_PRECEDENCE;
        }
        if(exp instanceof PsiTypeCastExpression ||
                exp instanceof PsiNewExpression){
            return TYPE_CAST_PRECEDENCE;
        }
        if(exp instanceof PsiPrefixExpression){
            return PREFIX_PRECEDENCE;
        }
        if(exp instanceof PsiPostfixExpression){
            return POSTFIX_PRECEDENCE;
        }
        if(exp instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) exp;
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            return precedenceForBinaryOperator(sign);
        }
        if(exp instanceof PsiInstanceOfExpression){
            return RELATIONAL_PRECEDENCE;
        }
        if(exp instanceof PsiConditionalExpression){
            return CONDITIONAL_EXPRESSION_EXPRESSION;
        }
        if(exp instanceof PsiAssignmentExpression){
            return ASSIGNMENT_EXPRESSION_PRECEDENCE;
        }
        if(exp instanceof PsiParenthesizedExpression){
            return PARENTHESIZED_EXPRESSION_PRECEDENCE;
        }
        return -1;
    }

    private static int precedenceForBinaryOperator(PsiJavaToken sign){
        final String operator = sign.getText();
        return s_binaryOperatorPrecedence.get(operator);
    }

    public static String removeParentheses(PsiExpression exp){
        if(exp instanceof PsiThisExpression ||
                exp instanceof PsiLiteralExpression ||
                exp instanceof PsiNewExpression ||
                exp instanceof PsiClassObjectAccessExpression ||
                exp instanceof PsiReferenceExpression ||
                exp instanceof PsiSuperExpression){
            return exp.getText();
        } else if(exp instanceof PsiMethodCallExpression){
            return removeParenthesesForMethodCall((PsiMethodCallExpression) exp);
        } else if(exp instanceof PsiAssignmentExpression){
            return removeParenthesesForAssignment((PsiAssignmentExpression) exp);
        } else if(exp instanceof PsiArrayInitializerExpression){
            return removeParenthesesForArrayInitializer((PsiArrayInitializerExpression) exp);
        } else if(exp instanceof PsiTypeCastExpression){
            return removeParenthesesForTypeCast((PsiTypeCastExpression) exp);
        } else if(exp instanceof PsiArrayAccessExpression){
            return removeParenthesesForArrayAccess((PsiArrayAccessExpression) exp);
        } else if(exp instanceof PsiPrefixExpression){
            return removeParenthesesForPrefixExpression((PsiPrefixExpression) exp);
        } else if(exp instanceof PsiPostfixExpression){
            return removeParenthesesForPostfixExpression((PsiPostfixExpression) exp);
        } else if(exp instanceof PsiBinaryExpression){
            return removeParenthesesForBinaryExpression((PsiBinaryExpression) exp);
        } else if(exp instanceof PsiInstanceOfExpression){
            return removeParenthesesForInstanceofExpression((PsiInstanceOfExpression) exp);
        } else if(exp instanceof PsiConditionalExpression){
            return removeParenthesesForConditionalExpression((PsiConditionalExpression) exp);
        } else if(exp instanceof PsiParenthesizedExpression){
            return removeParensFromParenthesizedExpression((PsiParenthesizedExpression) exp);
        }
        return exp.getText();  // this shouldn't happen
    }

    private static String removeParensFromParenthesizedExpression(
            PsiParenthesizedExpression parenthesizedExp){
        PsiExpression body = parenthesizedExp.getExpression();
        while(body instanceof PsiParenthesizedExpression){
            body = ((PsiParenthesizedExpression) body).getExpression();
        }
        if(!(parenthesizedExp.getParent() instanceof PsiExpression)){
            return removeParentheses(body);
        }
        final PsiExpression parentExp =
                (PsiExpression) parenthesizedExp.getParent();
        final int parentPrecedence = getPrecendence(parentExp);
        final int childPrecedence = getPrecendence(body);
        if(parentPrecedence < childPrecedence){
            return '(' + removeParentheses(body) + ')';
        } else if(parentPrecedence == childPrecedence){
            if(parentExp instanceof PsiBinaryExpression &&
                    body instanceof PsiBinaryExpression){
                final IElementType parentOperator =
                        ((PsiBinaryExpression) parentExp).getOperationSign()
                                .getTokenType();
                final IElementType bodyOperator =
                        ((PsiBinaryExpression) body).getOperationSign()
                                .getTokenType();

                final PsiExpression lhs =
                        ((PsiBinaryExpression) parentExp).getLOperand();

                if(lhs.equals(parenthesizedExp) &&
                        parentOperator.equals(bodyOperator)){
                    return removeParentheses(body);
                } else{
                    return '(' + removeParentheses(body) + ')';
                }
            } else{
                return removeParentheses(body);
            }
        } else{
            return removeParentheses(body);
        }
    }

    private static String removeParenthesesForConditionalExpression(PsiConditionalExpression conditionalExp){
        final PsiExpression condition = conditionalExp.getCondition();
        final PsiExpression thenBranch = conditionalExp.getThenExpression();
        final PsiExpression elseBranch = conditionalExp.getElseExpression();
        return removeParentheses(condition) + '?' +
                removeParentheses(thenBranch) + ':' +
                removeParentheses(elseBranch);
    }

    private static String removeParenthesesForInstanceofExpression(PsiInstanceOfExpression instanceofExp){
        final PsiExpression body = instanceofExp.getOperand();

        final PsiTypeElement checkType = instanceofExp.getCheckType();
        return removeParentheses(body) + " instanceof " + checkType.getText();
    }

    private static String removeParenthesesForBinaryExpression(PsiBinaryExpression binaryExp){
        final PsiExpression lhs = binaryExp.getLOperand();
        final PsiExpression rhs = binaryExp.getROperand();
        final PsiJavaToken sign = binaryExp.getOperationSign();
        return removeParentheses(lhs) + sign.getText() +
                removeParentheses(rhs);
    }

    private static String removeParenthesesForPostfixExpression(PsiPostfixExpression postfixExp){
        final PsiExpression body = postfixExp.getOperand();
        final PsiJavaToken sign = postfixExp.getOperationSign();
        final String operand = sign.getText();
        return removeParentheses(body) + operand;
    }

    private static String removeParenthesesForPrefixExpression(PsiPrefixExpression prefixExp){
        final PsiExpression body = prefixExp.getOperand();
        final PsiJavaToken sign = prefixExp.getOperationSign();
        final String operand = sign.getText();
        return operand + removeParentheses(body);
    }

    private static String removeParenthesesForArrayAccess(PsiArrayAccessExpression arrayAccessExp){
        final PsiExpression arrayExp = arrayAccessExp.getArrayExpression();
        final PsiExpression indexExp = arrayAccessExp.getIndexExpression();
        return removeParentheses(arrayExp) + '[' + removeParentheses(indexExp) +
                ']';
    }

    private static String removeParenthesesForTypeCast(PsiTypeCastExpression typeCast){
        final PsiExpression body = typeCast.getOperand();
        final PsiTypeElement type = typeCast.getCastType();
        return '(' + type.getText() + ')' + removeParentheses(body);
    }

    private static String removeParenthesesForArrayInitializer(PsiArrayInitializerExpression init){
        final PsiExpression[] contents = init.getInitializers();
        final String text = init.getText();
        final int originalLength = text.length();
        final StringBuffer out = new StringBuffer(originalLength);
        out.append('{');
        for(int i = 0; i < contents.length; i++){
            final PsiExpression arg = contents[i];
            if(i != 0){
                out.append(',');
            }
            final String strippedArg = removeParentheses(arg);
            out.append(strippedArg);
        }
        out.append('}');
        return out.toString();
    }

    private static String removeParenthesesForAssignment(PsiAssignmentExpression assignment){
        final PsiExpression lhs = assignment.getLExpression();
        final PsiExpression rhs = assignment.getRExpression();
        final PsiJavaToken sign = assignment.getOperationSign();
        return removeParentheses(lhs) + sign.getText() + removeParentheses(rhs);
    }

    private static String removeParenthesesForMethodCall(PsiMethodCallExpression methCall){
        final PsiReferenceExpression target = methCall.getMethodExpression();
        final PsiExpressionList argumentList = methCall.getArgumentList();
        assert argumentList != null;
        final PsiExpression[] args = argumentList.getExpressions();

        final String text = methCall.getText();
        final int length = text.length();
        final StringBuffer out = new StringBuffer(length);
        final String strippedTarget = removeParentheses(target);
        out.append(strippedTarget);
        out.append('(');
        for(int i = 0; i < args.length; i++){
            final PsiExpression arg = args[i];
            if(i != 0){
                out.append(',');
            }
            final String strippedArg = removeParentheses(arg);
            out.append(strippedArg);
        }
        out.append(')');
        return out.toString();
    }
}
