package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

public class BoolUtils{
    private BoolUtils(){
        super();
    }

    public static boolean isNegated(PsiExpression exp){
        PsiExpression ancestor = exp;
        while(ancestor.getParent() instanceof PsiParenthesizedExpression){
            ancestor = (PsiExpression) ancestor.getParent();
        }
        if(ancestor.getParent() instanceof PsiPrefixExpression){
            final PsiPrefixExpression prefixAncestor =
                    (PsiPrefixExpression) ancestor.getParent();
            final PsiJavaToken sign = prefixAncestor.getOperationSign();
            if(sign.getTokenType().equals(JavaTokenType.EXCL)){
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static PsiExpression findNegation(PsiExpression exp){
        PsiExpression ancestor = exp;
        while(ancestor.getParent() instanceof PsiParenthesizedExpression){
            ancestor = (PsiExpression) ancestor.getParent();
        }
        if(ancestor.getParent() instanceof PsiPrefixExpression){
            final PsiPrefixExpression prefixAncestor =
                    (PsiPrefixExpression) ancestor.getParent();
            final PsiJavaToken sign = prefixAncestor.getOperationSign();
            if(JavaTokenType.EXCL.equals(sign.getTokenType())){
                return prefixAncestor;
            }
        }
        return null;
    }

    public static boolean isNegation(PsiExpression exp){
        if(!(exp instanceof PsiPrefixExpression)){
            return false;
        }
        final PsiPrefixExpression prefixExp = (PsiPrefixExpression) exp;
        final PsiJavaToken sign = prefixExp.getOperationSign();
        return sign.getTokenType().equals(JavaTokenType.EXCL);
    }

    public static PsiExpression getNegated(PsiExpression exp){
        final PsiPrefixExpression prefixExp = (PsiPrefixExpression) exp;
        final PsiExpression operand = prefixExp.getOperand();
        return ParenthesesUtils.stripParentheses(operand);
    }

    public static boolean isBooleanLiteral(PsiExpression exp){
        if(exp instanceof PsiLiteralExpression){
            final PsiLiteralExpression expression = (PsiLiteralExpression) exp;
            final String text = expression.getText();
            return "true".equals(text) || "false".equals(text);
        }
        return false;
    }

    public static String getNegatedExpressionText(PsiExpression condition){
        if(BoolUtils.isNegation(condition)){
            final PsiExpression negated = getNegated(condition);
            return negated.getText();
        } else if(ComparisonUtils.isComparison(condition)){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) condition;
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final String operator = sign.getText();
            final String negatedComparison =
                    ComparisonUtils.getNegatedComparison(operator);
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            assert rhs != null;
            return lhs.getText() + negatedComparison + rhs.getText();
        } else if(ParenthesesUtils.getPrecendence(condition) >
                ParenthesesUtils.PREFIX_PRECEDENCE){
            return "!(" + condition.getText() + ')';
        } else{
            return '!' + condition.getText();
        }
    }
}
