package com.siyeh.ig.psiutils;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPrefixExpression;

public class BoolUtils {
    private BoolUtils() {
        super();
    }

    private static boolean isNegation(PsiExpression exp) {
        if (!(exp instanceof PsiPrefixExpression)) {
            return false;
        }
        final PsiPrefixExpression prefixExp = (PsiPrefixExpression) exp;
        final PsiJavaToken sign = prefixExp.getOperationSign();
        return sign.getTokenType() == JavaTokenType.EXCL;
    }

    private static PsiExpression getNegated(PsiExpression exp) {
        final PsiPrefixExpression prefixExp = (PsiPrefixExpression) exp;
        final PsiExpression operand = prefixExp.getOperand();
        return ParenthesesUtils.stripParentheses(operand);
    }

    public static String getNegatedExpressionText(PsiExpression condition) {
        if (isNegation(condition)) {
            final PsiExpression negatedCondition = getNegated(condition);
            return negatedCondition.getText();
        } else if (ParenthesesUtils.getPrecendence(condition) >
                ParenthesesUtils.PREFIX_PRECEDENCE) {
            return "!(" + condition.getText() + ')';
        } else {
            return '!' + condition.getText();
        }

    }

    public static boolean isTrue(PsiExpression test) {
        if (test == null) {
            return false;
        }
        final String text = test.getText();
        if ("true".equals(text)) {
            return true;

        }
        return false;
    }
}
