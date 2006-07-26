/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ParenthesesUtils{

    private ParenthesesUtils(){
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
    public static final int AND_PRECEDENCE = 13;
    public static final int OR_PRECEDENCE = 14;
    public static final int CONDITIONAL_PRECEDENCE = 15;
    private static final int ASSIGNMENT_PRECEDENCE = 16;

    private static final int NUM_PRECEDENCES = 17;

    private static final Map<String, Integer> s_binaryOperatorPrecedence =
            new HashMap<String, Integer>(NUM_PRECEDENCES);

    static {
        s_binaryOperatorPrecedence.put("+", ADDITIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("-", ADDITIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("*", MULTIPLICATIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("/", MULTIPLICATIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("%", MULTIPLICATIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("&&", AND_PRECEDENCE);
        s_binaryOperatorPrecedence.put("||", OR_PRECEDENCE);
        s_binaryOperatorPrecedence.put("&", BINARY_AND_PRECEDENCE);
        s_binaryOperatorPrecedence.put("|", BINARY_OR_PRECEDENCE);
        s_binaryOperatorPrecedence.put("^", BINARY_XOR_PRECEDENCE);
        s_binaryOperatorPrecedence.put("<<", SHIFT_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">>", SHIFT_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">>>", SHIFT_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">", RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">=", RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put("<", RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put("<=", RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put("==", EQUALITY_PRECEDENCE);
        s_binaryOperatorPrecedence.put("!=", EQUALITY_PRECEDENCE);
    }

    @Nullable
    public static PsiExpression stripParentheses(
            @Nullable PsiExpression expression){
        PsiExpression parenthesized = expression;
        while(parenthesized instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)parenthesized;
            parenthesized = parenthesizedExpression.getExpression();
        }
        return parenthesized;
    }

    public static int getPrecedence(PsiExpression expression){
        if(expression instanceof PsiThisExpression ||
           expression instanceof PsiLiteralExpression ||
           expression instanceof PsiSuperExpression ||
           expression instanceof PsiClassObjectAccessExpression ||
           expression instanceof PsiArrayAccessExpression ||
           expression instanceof PsiArrayInitializerExpression){
            return LITERAL_PRECEDENCE;
        }
        if(expression instanceof PsiReferenceExpression){
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)expression;
            if(referenceExpression.getQualifier() != null){
                return METHOD_CALL_PRECEDENCE;
            } else{
                return LITERAL_PRECEDENCE;
            }
        }
        if(expression instanceof PsiMethodCallExpression){
            return METHOD_CALL_PRECEDENCE;
        }
        if(expression instanceof PsiTypeCastExpression ||
           expression instanceof PsiNewExpression){
            return TYPE_CAST_PRECEDENCE;
        }
        if(expression instanceof PsiPrefixExpression){
            return PREFIX_PRECEDENCE;
        }
        if(expression instanceof PsiPostfixExpression){
            return POSTFIX_PRECEDENCE;
        }
        if(expression instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final PsiJavaToken sign =
                    binaryExpression.getOperationSign();
            return precedenceForBinaryOperator(sign);
        }
        if(expression instanceof PsiInstanceOfExpression){
            return RELATIONAL_PRECEDENCE;
        }
        if(expression instanceof PsiConditionalExpression){
            return CONDITIONAL_PRECEDENCE;
        }
        if(expression instanceof PsiAssignmentExpression){
            return ASSIGNMENT_PRECEDENCE;
        }
        if(expression instanceof PsiParenthesizedExpression){
            return PARENTHESIZED_PRECEDENCE;
        }
        return -1;
    }

    private static int precedenceForBinaryOperator(@NotNull PsiJavaToken sign){
        final String operator = sign.getText();
        final Integer precedence = s_binaryOperatorPrecedence.get(operator);
        return precedence.intValue();
    }

    public static String removeParentheses(@Nullable PsiExpression expression){
        if (expression == null){
            return "";
        }
        if(expression instanceof PsiMethodCallExpression){
            final PsiMethodCallExpression methCall =
                    (PsiMethodCallExpression)expression;
            return removeParensFromMethodCallExpression(methCall);
        }
        if(expression instanceof PsiReferenceExpression){
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)expression;
            return removeParensFromReferenceExpression(referenceExpression);
        }
        if(expression instanceof PsiNewExpression){
            final PsiNewExpression newExpression = (PsiNewExpression)expression;
            return removeParensFromNewExpression(newExpression);
        }
        if(expression instanceof PsiAssignmentExpression){
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression)expression;
            return removeParensFromAssignmentExpression(assignmentExpression);
        }
        if(expression instanceof PsiArrayInitializerExpression){
            final PsiArrayInitializerExpression arrayInitializerExpression =
                    (PsiArrayInitializerExpression)expression;
            return removeParensFromArrayInitializerExpression(
                    arrayInitializerExpression);
        }
        if(expression instanceof PsiTypeCastExpression){
            final PsiTypeCastExpression typeCastExpression =
                    (PsiTypeCastExpression)expression;
            return removeParensFromTypeCastExpression(typeCastExpression);
        }
        if(expression instanceof PsiArrayAccessExpression){
            final PsiArrayAccessExpression arrayAccessExpression =
                    (PsiArrayAccessExpression)expression;
            return removeParensFromArrayAccessExpression(arrayAccessExpression);
        }
        if(expression instanceof PsiPrefixExpression){
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression)expression;
            return removeParensFromPrefixExpression(prefixExpression);
        }
        if(expression instanceof PsiPostfixExpression){
            final PsiPostfixExpression postfixExpression =
                    (PsiPostfixExpression)expression;
            return removeParensFromPostfixExpression(postfixExpression);
        }
        if(expression instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            return removeParensFromBinaryExpression(binaryExpression);
        }
        if(expression instanceof PsiInstanceOfExpression){
            final PsiInstanceOfExpression instanceofExpression =
                    (PsiInstanceOfExpression)expression;
            return removeParensFromInstanceOfExpression(instanceofExpression);
        }
        if(expression instanceof PsiConditionalExpression){
            final PsiConditionalExpression conditionalExpression =
                    (PsiConditionalExpression)expression;
            return removeParensFromConditionalExpression(conditionalExpression);
        }
        if(expression instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expression;
            return removeParensFromParenthesizedExpression(
                    parenthesizedExpression);
        }
        return expression.getText();
    }

    private static String removeParensFromReferenceExpression(
            @NotNull PsiReferenceExpression referenceExpression){
        final PsiExpression qualifier =
                referenceExpression.getQualifierExpression();
        if(qualifier != null){
            final PsiType[] typeParameters =
                    referenceExpression.getTypeParameters();
            if (typeParameters.length > 0) {
                final StringBuilder result = new StringBuilder();
                result.append(removeParentheses(qualifier));
                result.append(".<");
                result.append(typeParameters[0].getCanonicalText());
                for (int i = 1; i < typeParameters.length; i++) {
                    final PsiType typeParameter = typeParameters[i];
                    result.append(',');
                    result.append(typeParameter.getCanonicalText());
                }
                result.append('>');
                result.append(referenceExpression.getReferenceName());
                return result.toString();
            } else {
                return removeParentheses(qualifier) + '.' +
                        referenceExpression.getReferenceName();
            }
        } else{
            return referenceExpression.getText();
        }
    }

    private static String removeParensFromParenthesizedExpression(
            @NotNull PsiParenthesizedExpression parenthesizedExpression){
        PsiExpression body = parenthesizedExpression.getExpression();
        while(body instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression innerParenthesizedExpression =
                    (PsiParenthesizedExpression)body;
            body = innerParenthesizedExpression.getExpression();
        }
        if(!(parenthesizedExpression.getParent() instanceof PsiExpression)){
            return removeParentheses(body);
        }
        final PsiExpression parentExpression =
                (PsiExpression) parenthesizedExpression.getParent();
        final int parentPrecedence = getPrecedence(parentExpression);
        final int childPrecedence = getPrecedence(body);
        if(parentPrecedence < childPrecedence){
            return '(' + removeParentheses(body) + ')';
        } else if(parentPrecedence == childPrecedence){
            if(parentExpression instanceof PsiBinaryExpression &&
               body instanceof PsiBinaryExpression){
                final PsiBinaryExpression parentBinaryExpression =
                        (PsiBinaryExpression)parentExpression;
                final PsiJavaToken parentBinaryOperationSign =
                        parentBinaryExpression.getOperationSign();
                final IElementType parentOperator =
                        parentBinaryOperationSign.getTokenType();
                final PsiBinaryExpression bodyBinaryExpression =
                        (PsiBinaryExpression)body;
                final PsiJavaToken bodyBinaryOperationSign =
                        bodyBinaryExpression.getOperationSign();
                final IElementType bodyOperator =
                        bodyBinaryOperationSign.getTokenType();
                final PsiType parentType = parentBinaryExpression.getType();
                final PsiType bodyType = body.getType();
                if(parentType != null && parentType.equals(bodyType) &&
                        parentOperator.equals(bodyOperator)) {
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

    private static String removeParensFromConditionalExpression(
            @NotNull PsiConditionalExpression conditionalExpression){
        final PsiExpression condition = conditionalExpression.getCondition();
        final PsiExpression thenBranch =
                conditionalExpression.getThenExpression();
        final PsiExpression elseBranch =
                conditionalExpression.getElseExpression();
        return removeParentheses(condition) + '?' +
               removeParentheses(thenBranch) + ':' +
               removeParentheses(elseBranch);
    }

    private static String removeParensFromInstanceOfExpression(
            @NotNull PsiInstanceOfExpression instanceofExpression){
        final PsiExpression body = instanceofExpression.getOperand();
        final PsiTypeElement type = instanceofExpression.getCheckType();
        final String typeText;
        if (type == null) {
            typeText = "";
        } else {
            typeText = type.getText();
        }
        return removeParentheses(body) + ' ' + PsiKeyword.INSTANCEOF + ' ' +
               typeText;
    }

    private static String removeParensFromBinaryExpression(
            @NotNull PsiBinaryExpression binaryExpression){
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        return removeParentheses(lhs) + ' ' + sign.getText() + ' '
               + removeParentheses(rhs);
    }

    private static String removeParensFromPostfixExpression(
            @NotNull PsiPostfixExpression postfixExpression){
        final PsiExpression body = postfixExpression.getOperand();
        final PsiJavaToken sign = postfixExpression.getOperationSign();
        final String operand = sign.getText();
        return removeParentheses(body) + operand;
    }

    private static String removeParensFromPrefixExpression(
            @NotNull PsiPrefixExpression prefixExpression){
        final PsiExpression body = prefixExpression.getOperand();
        final PsiJavaToken sign = prefixExpression.getOperationSign();
        final String operand = sign.getText();
        return operand + removeParentheses(body);
    }

    private static String removeParensFromArrayAccessExpression(
            @NotNull PsiArrayAccessExpression arrayAccessExpression){
        final PsiExpression arrayExp =
                arrayAccessExpression.getArrayExpression();
        final PsiExpression indexExp =
                arrayAccessExpression.getIndexExpression();
        return removeParentheses(arrayExp) + '[' + removeParentheses(indexExp) +
               ']';
    }

    private static String removeParensFromTypeCastExpression(
            @NotNull PsiTypeCastExpression typeCastExpression){
        final PsiExpression body = typeCastExpression.getOperand();
        final PsiTypeElement type = typeCastExpression.getCastType();
        final String typeText;
        if (type == null) {
            typeText =  "";
        } else {
            typeText = type.getText();
        }
        return '(' + typeText + ')' + removeParentheses(body);
    }

    private static String removeParensFromArrayInitializerExpression(
            @NotNull PsiArrayInitializerExpression arrayInitializerExpression){
        final PsiExpression[] contents =
                arrayInitializerExpression.getInitializers();
        final String text = arrayInitializerExpression.getText();
        final int textLength = text.length();
        final StringBuffer out = new StringBuffer(textLength);
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

    private static String removeParensFromAssignmentExpression(
            @NotNull PsiAssignmentExpression assignment){
        final PsiExpression lhs = assignment.getLExpression();
        final PsiExpression rhs = assignment.getRExpression();
        final PsiJavaToken sign = assignment.getOperationSign();
        return removeParentheses(lhs) + ' ' + sign.getText() + ' ' +
               removeParentheses(rhs);
    }

    private static String removeParensFromNewExpression(
            @NotNull PsiNewExpression newExpression){
        final PsiExpression[] dimensions = newExpression.getArrayDimensions();
        final String[] strippedDimensions = new String[dimensions.length];
        for(int i = 0; i < dimensions.length; i++){
            strippedDimensions[i] = removeParentheses(dimensions[i]);
        }
        final PsiExpression qualifier = newExpression.getQualifier();
        final PsiExpression arrayInitializer =
                newExpression.getArrayInitializer();
        String strippedInitializer = null;
        if(arrayInitializer != null){
            strippedInitializer = removeParentheses(arrayInitializer);
        }
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        String[] strippedArgs = null;
        if(argumentList != null){
            final PsiExpression[] args = argumentList.getExpressions();
            strippedArgs = new String[args.length];
            for(int i = 0; i < args.length; i++){
                strippedArgs[i] = removeParentheses(args[i]);
            }
        }
        final String expressionText = newExpression.getText();
        if(qualifier != null){
            return expressionText;
        }
        final PsiElement[] children = newExpression.getChildren();
        for(final PsiElement child : children){
            if(child instanceof PsiAnonymousClass){
                return expressionText;
            }
        }
        final int length = expressionText.length();
        final StringBuffer out = new StringBuffer(length);
        out.append(PsiKeyword.NEW + ' ');
        final PsiJavaCodeReferenceElement classReference =
                newExpression.getClassReference();
        final String text;
        if(classReference == null){
            text = "";
        } else {
            text = classReference.getText();
        }
        out.append(text);
        if(strippedArgs != null){
            out.append('(');
            for(int i = 0; i < strippedArgs.length; i++){
                if(i != 0){
                    out.append(',');
                }
                out.append(strippedArgs[i]);
            }
            out.append(')');
        }
        final PsiType type = newExpression.getType();
        if(strippedDimensions.length > 0){
            for(String strippedDimension : strippedDimensions){
                out.append('[');
                out.append(strippedDimension);
                out.append(']');
            }
        } else if (type != null){
            final int dimensionCount = type.getArrayDimensions();
            for(int i = 0; i < dimensionCount; i++){
                out.append("[]");
            }
        }
        if(strippedInitializer != null){
            out.append(strippedInitializer);
        }
        return out.toString();
    }

    private static String removeParensFromMethodCallExpression(
            @NotNull PsiMethodCallExpression methodCallExpression){
        final PsiReferenceExpression target =
                methodCallExpression.getMethodExpression();
        final PsiExpressionList argumentList =
                methodCallExpression.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final String methodCallText = methodCallExpression.getText();
        final int length = methodCallText.length();
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
