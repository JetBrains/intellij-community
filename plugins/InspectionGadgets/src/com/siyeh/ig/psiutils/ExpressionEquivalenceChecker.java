package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class ExpressionEquivalenceChecker {
    private ExpressionEquivalenceChecker() {
        super();

    }

    private static final int THIS_EXPRESSION = 0;
    private static final int LITERAL_EXPRESSION = 1;
    private static final int CLASS_OBJECT_EXPRESSION = 2;
    private static final int REFERENCE_EXPRESSION = 3;
    private static final int SUPER_EXPRESSION = 4;
    private static final int METHOD_CALL_EXPRESSION = 5;
    private static final int NEW_EXPRESSION = 6;
    private static final int ARRAY_INITIALIZER_EXPRESSION = 7;
    private static final int TYPECAST_EXPRESSION = 8;
    private static final int ARRAY_ACCESS_EXPRESSION = 9;
    private static final int PREFIX_EXPRESSION = 10;
    private static final int POSTFIX_EXPRESSION = 11;
    private static final int BINARY_EXPRESSION = 12;
    private static final int CONDITIONAL_EXPRESSION = 13;
    private static final int ASSIGNMENT_EXPRESSION = 14;

    public static boolean expressionsAreEquivalent(PsiExpression exp1, PsiExpression exp2) {
        if (exp1 == null && exp2 == null) {
            return true;
        }
        if (exp1 == null || exp2 == null) {
            return false;
        }
        final PsiExpression expToCompare1 = stripParentheses(exp1);
        final PsiExpression expToCompare2 = stripParentheses(exp2);
        final int type1 = getExpressionType(expToCompare1);
        final int type2 = getExpressionType(expToCompare2);
        if (type1 != type2) {
            return false;
        }
        switch (type1) {
            case THIS_EXPRESSION:
            case SUPER_EXPRESSION:
                return true;
            case LITERAL_EXPRESSION:
            case CLASS_OBJECT_EXPRESSION:
                final String text1 = expToCompare1.getText();
                final String text2 = expToCompare2.getText();
                return text1.equals(text2);
            case REFERENCE_EXPRESSION:
                return referenceExpressionsAreEquivalent((PsiReferenceExpression) expToCompare1,
                        (PsiReferenceExpression) expToCompare2);
            case METHOD_CALL_EXPRESSION:
                return methodCallExpressionsAreEquivalent((PsiMethodCallExpression) expToCompare1,
                        (PsiMethodCallExpression) expToCompare2);
            case NEW_EXPRESSION:
                return newExpressionsAreEquivalent((PsiNewExpression) expToCompare1,
                        (PsiNewExpression) expToCompare2);
            case ARRAY_INITIALIZER_EXPRESSION:
                return arrayInitializerExpressionsAreEquivalent((PsiArrayInitializerExpression) expToCompare1,
                        (PsiArrayInitializerExpression) expToCompare2);
            case TYPECAST_EXPRESSION:
                return typecastExpressionsAreEquivalent((PsiTypeCastExpression) expToCompare2,
                        (PsiTypeCastExpression) expToCompare1);
            case ARRAY_ACCESS_EXPRESSION:
                return arrayAccessExpressionsAreEquivalent((PsiArrayAccessExpression) expToCompare2,
                        (PsiArrayAccessExpression) expToCompare1);
            case PREFIX_EXPRESSION:
                return prefixExpressionsAreEquivalent((PsiPrefixExpression) expToCompare1,
                        (PsiPrefixExpression) expToCompare2);
            case POSTFIX_EXPRESSION:
                return postfixExpressionsAreEquivalent((PsiPostfixExpression) expToCompare1,
                        (PsiPostfixExpression) expToCompare2);
            case BINARY_EXPRESSION:
                return binaryExpressionsAreEquivalent((PsiBinaryExpression) expToCompare1,
                        (PsiBinaryExpression) expToCompare2);
            case ASSIGNMENT_EXPRESSION:
                return assignmentExpressionsAreEquivalent((PsiAssignmentExpression) expToCompare1,
                        (PsiAssignmentExpression) expToCompare2);
            case CONDITIONAL_EXPRESSION:
                return conditionalExpressionsAreEquivalent((PsiConditionalExpression) expToCompare1,
                        (PsiConditionalExpression) expToCompare2);
            default:
                return false;
        }
    }

    private static PsiExpression stripParentheses(PsiExpression exp) {
        PsiExpression strippedExpression = exp;
        while (strippedExpression instanceof PsiParenthesizedExpression) {
            final PsiParenthesizedExpression parenExp = (PsiParenthesizedExpression) strippedExpression;
            strippedExpression = parenExp.getExpression();
        }
        return strippedExpression;
    }

    private static boolean referenceExpressionsAreEquivalent(PsiReferenceExpression expToCompare1, PsiReferenceExpression expToCompare2) {
        final String referenceName1 = expToCompare1.getReferenceName();
        final String referenceName2 = expToCompare2.getReferenceName();
        if (!referenceName1.equals(referenceName2)) {
            return false;
        }
        final PsiExpression qualifier1 = expToCompare1.getQualifierExpression();
        final PsiExpression qualifier2 = expToCompare2.getQualifierExpression();

        return expressionsAreEquivalent(qualifier1, qualifier2);
    }

    private static boolean methodCallExpressionsAreEquivalent(PsiMethodCallExpression methodExp1,
                                                              PsiMethodCallExpression methodExp2) {
        final PsiReferenceExpression methodExpression1 = methodExp1.getMethodExpression();
        final PsiReferenceExpression methodExpression2 = methodExp2.getMethodExpression();
        if (!expressionsAreEquivalent(methodExpression1, methodExpression2)) {
            return false;
        }
        final PsiExpressionList argumentList1 = methodExp1.getArgumentList();
        final PsiExpression[] args1 = argumentList1.getExpressions();
        final PsiExpressionList argumentList2 = methodExp2.getArgumentList();
        final PsiExpression[] args2 = argumentList2.getExpressions();
        return expressionListsAreEquivalent(args1, args2);
    }

    private static boolean newExpressionsAreEquivalent(PsiNewExpression newExp1, PsiNewExpression newExp2) {
        final PsiExpression[] dimensions1 = newExp1.getArrayDimensions();
        final PsiExpression[] dimensions2 = newExp2.getArrayDimensions();
        if (!expressionListsAreEquivalent(dimensions1, dimensions2)) {
            return false;
        }
        final PsiArrayInitializerExpression initializer1 = newExp1.getArrayInitializer();
        final PsiArrayInitializerExpression initializer2 = newExp2.getArrayInitializer();
        if (!expressionsAreEquivalent(initializer1, initializer2)) {
            return false;
        }
        final PsiExpression qualifier1 = newExp1.getQualifier();
        final PsiExpression qualifier2 = newExp2.getQualifier();
        if (!expressionsAreEquivalent(qualifier1, qualifier2)) {
            return false;
        }
        final PsiExpressionList argumentList1 = newExp1.getArgumentList();
        final PsiExpression[] args1 = argumentList1.getExpressions();
        final PsiExpressionList argumentList2 = newExp2.getArgumentList();
        final PsiExpression[] args2 = argumentList2.getExpressions();
        return expressionListsAreEquivalent(args1,
                args2);
    }

    private static boolean arrayInitializerExpressionsAreEquivalent(PsiArrayInitializerExpression arrInitExp1,
                                                                    PsiArrayInitializerExpression arrInitExp2) {
        final PsiExpression[] initializers1 = arrInitExp1.getInitializers();
        final PsiExpression[] initializers2 = arrInitExp2.getInitializers();
        return expressionListsAreEquivalent(initializers1, initializers2);
    }

    private static boolean typecastExpressionsAreEquivalent(PsiTypeCastExpression typecastExp2,
                                                            PsiTypeCastExpression typecastExp1) {
        final PsiTypeElement castType2 = typecastExp2.getCastType();
        final String castTypeText2 = castType2.getText();
        final PsiTypeElement castType1 = typecastExp1.getCastType();
        final String castTypeText1 = castType1.getText();
        if (!castTypeText2.equals(castTypeText1)) {
            return false;
        }
        final PsiExpression operand1 = typecastExp1.getOperand();
        final PsiExpression operand2 = typecastExp2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean arrayAccessExpressionsAreEquivalent(PsiArrayAccessExpression arrAccessExp2,
                                                               PsiArrayAccessExpression arrAccessExp1) {
        final PsiExpression array1 = arrAccessExp2.getArrayExpression();
        final PsiExpression array2 = arrAccessExp1.getArrayExpression();
        final PsiExpression index1 = arrAccessExp1.getIndexExpression();
        final PsiExpression index2 = arrAccessExp2.getIndexExpression();
        return expressionsAreEquivalent(array1, array2)
                && expressionsAreEquivalent(index1, index2);
    }

    private static boolean prefixExpressionsAreEquivalent(PsiPrefixExpression prefixExp1,
                                                          PsiPrefixExpression prefixExp2) {
        final PsiJavaToken sign1 = prefixExp1.getOperationSign();
        final PsiJavaToken sign2 = prefixExp2.getOperationSign();
        if (!sign1.getTokenType().equals(sign2.getTokenType())) {
            return false;
        }
        final PsiExpression operand1 = prefixExp1.getOperand();
        final PsiExpression operand2 = prefixExp2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean postfixExpressionsAreEquivalent(PsiPostfixExpression postfixExp1,
                                                           PsiPostfixExpression postfixExp2) {
        final PsiJavaToken sign1 = postfixExp1.getOperationSign();
        final PsiJavaToken sign2 = postfixExp2.getOperationSign();
        if (!sign1.getTokenType().equals(sign2.getTokenType())) {
            return false;
        }
        final PsiExpression operand1 = postfixExp1.getOperand();
        final PsiExpression operand2 = postfixExp2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean binaryExpressionsAreEquivalent(PsiBinaryExpression binaryExp1,
                                                          PsiBinaryExpression binaryExp2) {
        final PsiJavaToken sign1 = binaryExp1.getOperationSign();
        final PsiJavaToken sign2 = binaryExp2.getOperationSign();
        if (!sign1.getTokenType().equals(sign2.getTokenType())) {
            return false;
        }
        final PsiExpression lhs1 = binaryExp1.getLOperand();
        final PsiExpression lhs2 = binaryExp2.getLOperand();
        final PsiExpression rhs1 = binaryExp1.getROperand();
        final PsiExpression rhs2 = binaryExp2.getROperand();
        return expressionsAreEquivalent(lhs1, lhs2)
                && expressionsAreEquivalent(rhs1, rhs2);
    }

    private static boolean assignmentExpressionsAreEquivalent(PsiAssignmentExpression assignExp1,
                                                              PsiAssignmentExpression assignExp2) {
        final PsiJavaToken sign1 = assignExp1.getOperationSign();
        final PsiJavaToken sign2 = assignExp2.getOperationSign();
        if (!sign1.getTokenType().equals(sign2.getTokenType())) {
            return false;
        }
        final PsiExpression lhs1 = assignExp1.getLExpression();
        final PsiExpression lhs2 = assignExp2.getLExpression();
        final PsiExpression rhs1 = assignExp1.getRExpression();
        final PsiExpression rhs2 = assignExp2.getRExpression();
        return expressionsAreEquivalent(lhs1, lhs2)
                && expressionsAreEquivalent(rhs1, rhs2);
    }

    private static boolean conditionalExpressionsAreEquivalent(PsiConditionalExpression condExp1,
                                                               PsiConditionalExpression condExp2) {
        final PsiExpression cond1 = condExp1.getCondition();
        final PsiExpression cond2 = condExp2.getCondition();
        final PsiExpression then1 = condExp1.getThenExpression();
        final PsiExpression else1 = condExp1.getElseExpression();
        final PsiExpression then2 = condExp2.getThenExpression();
        final PsiExpression else2 = condExp2.getElseExpression();
        return expressionsAreEquivalent(cond1, cond2)
                && expressionsAreEquivalent(then1, then2)
                && expressionsAreEquivalent(else1, else2);
    }

    private static boolean expressionListsAreEquivalent(PsiExpression[] expressions1, PsiExpression[] expressions2) {
        if (expressions1 == null && expressions2 == null) {
            return true;
        }
        if (expressions1 == null || expressions2 == null) {
            return false;
        }
        if (expressions1.length != expressions2.length) {
            return false;
        }
        for (int i = 0; i < expressions1.length; i++) {
            if (!expressionsAreEquivalent(expressions1[i], expressions2[i])) {
                return false;
            }
        }
        return true;
    }

    private static int getExpressionType(PsiExpression exp) {
        if (exp instanceof PsiThisExpression) {
            return THIS_EXPRESSION;
        }
        if (exp instanceof PsiLiteralExpression) {
            return LITERAL_EXPRESSION;
        }
        if (exp instanceof PsiClassObjectAccessExpression) {
            return CLASS_OBJECT_EXPRESSION;
        }
        if (exp instanceof PsiReferenceExpression) {
            return REFERENCE_EXPRESSION;
        }
        if (exp instanceof PsiSuperExpression) {
            return SUPER_EXPRESSION;
        }
        if (exp instanceof PsiMethodCallExpression) {
            return METHOD_CALL_EXPRESSION;
        }
        if (exp instanceof PsiNewExpression) {
            return NEW_EXPRESSION;
        }
        if (exp instanceof PsiArrayInitializerExpression) {
            return ARRAY_INITIALIZER_EXPRESSION;
        }
        if (exp instanceof PsiTypeCastExpression) {
            return TYPECAST_EXPRESSION;
        }
        if (exp instanceof PsiArrayAccessExpression) {
            return ARRAY_ACCESS_EXPRESSION;
        }
        if (exp instanceof PsiPrefixExpression) {
            return PREFIX_EXPRESSION;
        }
        if (exp instanceof PsiPostfixExpression) {
            return POSTFIX_EXPRESSION;
        }
        if (exp instanceof PsiBinaryExpression) {
            return BINARY_EXPRESSION;
        }
        if (exp instanceof PsiConditionalExpression) {
            return CONDITIONAL_EXPRESSION;
        }
        if (exp instanceof PsiAssignmentExpression) {
            return ASSIGNMENT_EXPRESSION;
        }
        return -1;
    }
}
