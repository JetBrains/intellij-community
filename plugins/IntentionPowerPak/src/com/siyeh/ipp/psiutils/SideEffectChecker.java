package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

public class SideEffectChecker
{
    private SideEffectChecker()
    {
        super();
    }

    public static boolean mayHaveSideEffects(PsiExpression exp)
    {
        if(exp instanceof PsiThisExpression ||
                exp instanceof PsiLiteralExpression ||
                exp instanceof PsiClassObjectAccessExpression ||
                exp instanceof PsiReferenceExpression ||
                exp instanceof PsiSuperExpression)
        {
            return false;
        }
        else if(exp instanceof PsiMethodCallExpression ||
                exp instanceof PsiNewExpression ||
                exp instanceof PsiAssignmentExpression ||
                exp instanceof PsiArrayInitializerExpression)
        {
            return true;
        }
        else if(exp instanceof PsiTypeCastExpression)
        {
            final PsiExpression body = ((PsiTypeCastExpression) exp).getOperand();
            return mayHaveSideEffects(body);
        }
        else if(exp instanceof PsiArrayAccessExpression)
        {
            final PsiArrayAccessExpression arrayAccessExp = (PsiArrayAccessExpression) exp;
            final PsiExpression arrayExp = arrayAccessExp.getArrayExpression();
            final PsiExpression indexExp = arrayAccessExp.getIndexExpression();
            return mayHaveSideEffects(arrayExp) || mayHaveSideEffects(indexExp);
        }
        else if(exp instanceof PsiPrefixExpression)
        {
            final PsiPrefixExpression prefixExp = (PsiPrefixExpression) exp;
            final PsiExpression body = prefixExp.getOperand();
            if(mayHaveSideEffects(body))
            {
                return true;
            }
            final PsiJavaToken sign = prefixExp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            return tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS;
        }
        else if(exp instanceof PsiPostfixExpression)
        {
            final PsiPostfixExpression postfixExp = (PsiPostfixExpression) exp;
            final PsiExpression body = postfixExp.getOperand();
            if(mayHaveSideEffects(body))
            {
                return true;
            }
            final PsiJavaToken sign = postfixExp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            return tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS;
        }
        else if(exp instanceof PsiBinaryExpression)
        {
            final PsiBinaryExpression binaryExp = (PsiBinaryExpression) exp;
            final PsiExpression lhs = binaryExp.getLOperand();
            final PsiExpression rhs = binaryExp.getROperand();
            return mayHaveSideEffects(lhs) ||
                    mayHaveSideEffects(rhs);
        }
        else if(exp instanceof PsiInstanceOfExpression)
        {
            final PsiExpression body = ((PsiInstanceOfExpression) exp).getOperand();
            return mayHaveSideEffects(body);
        }
        else if(exp instanceof PsiConditionalExpression)
        {
            final PsiConditionalExpression conditionalExp = (PsiConditionalExpression) exp;
            final PsiExpression condition = conditionalExp.getCondition();
            final PsiExpression thenBranch = conditionalExp.getThenExpression();
            final PsiExpression elseBranch = conditionalExp.getElseExpression();
            return mayHaveSideEffects(condition) ||
                    mayHaveSideEffects(thenBranch) ||
                    mayHaveSideEffects(elseBranch);
        }
        else if(exp instanceof PsiParenthesizedExpression)
        {
            final PsiExpression body = ((PsiParenthesizedExpression) exp).getExpression();
            return mayHaveSideEffects(body);
        }
        return true;   // this shouldn't happen
    }
}
