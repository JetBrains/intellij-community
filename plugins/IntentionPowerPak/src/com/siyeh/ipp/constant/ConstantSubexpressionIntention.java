/**
 * (c) 2005 Carp Technologies BV
 * Hengelosestraat 705, 7521PA Enschede
 * Created: Apr 14, 2005, 4:47:42 PM
 */
package com.siyeh.ipp.constant;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.concatenation.ConcatenationUtils;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class ConstantSubexpressionIntention extends MutablyNamedIntention{
    protected PsiElementPredicate getElementPredicate(){
        return new ConstantSubexpressionPredicate();
    }

    protected String getTextForElement(PsiElement element){
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) element.getParent();
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression leftSide;
        if(lhs instanceof PsiBinaryExpression){
            final PsiBinaryExpression lhsBinaryExpression = (PsiBinaryExpression) lhs;
            leftSide = lhsBinaryExpression.getROperand();
        } else{
            leftSide = lhs;
        }
        final PsiJavaToken operationSign = binaryExpression.getOperationSign();
        final PsiExpression rhs = binaryExpression.getROperand();
        assert rhs != null;
        assert leftSide != null;
        return "Compute constant value of " + leftSide.getText() + ' ' +
                operationSign.getText() + ' ' + rhs.getText();
    }

    public String getFamilyName(){
        return "Compute Constant Value For Subexpression";
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiManager manager = element.getManager();
        final PsiConstantEvaluationHelper constantEvaluationHelper =
                manager.getConstantEvaluationHelper();
        final PsiExpression expression = (PsiExpression) element.getParent();
        String newExpression = "";
        final Object constantValue;

        if(expression instanceof PsiBinaryExpression){
            final PsiBinaryExpression copy = (PsiBinaryExpression) expression.copy();
            final PsiExpression lhs = copy.getLOperand();
            if(lhs instanceof PsiBinaryExpression){
                final PsiBinaryExpression lhsBinaryExpression = (PsiBinaryExpression) lhs;
                newExpression += getLeftSideText(lhsBinaryExpression);
                final PsiExpression rightSide = lhsBinaryExpression.getROperand();
                lhs.replace(rightSide);
            }
            if(ConcatenationUtils.isConcatenation(expression)){
                constantValue = computeConstantStringExpression(copy);
            } else{
                constantValue = constantEvaluationHelper.computeConstantExpression(copy);
            }
        } else{
            constantValue = constantEvaluationHelper.computeConstantExpression(expression);
        }

        if(constantValue instanceof String){
            newExpression += '"' + StringUtil
                    .escapeStringCharacters(constantValue.toString()) + '"';
        } else{
            newExpression += constantValue.toString();
        }
        replaceExpression(newExpression, expression);
    }

    /**
     * handles the specified expression as if it was part of a string expression
     * (even if it's of another type) and computes a constant string expression
     * from it.
     */
    private static String computeConstantStringExpression(PsiBinaryExpression expression){
        final PsiExpression lhs = expression.getLOperand();
        final String lhsText = lhs.getText();
        String result;
        if(lhsText.charAt(0) == '\'' || lhsText.charAt(0) == '"'){
            result = lhsText.substring(1, lhsText.length() - 1);
        } else{
            result = lhsText;
        }
        final PsiExpression rhs = expression.getROperand();
        assert rhs != null;
        final String rhsText = rhs.getText();
        if(rhsText.charAt(0) == '\'' || rhsText.charAt(0) == '"'){
            result += rhsText.substring(1, rhsText.length() - 1);
        } else{
            result += rhsText;
        }
        return result;
    }

    private static String getLeftSideText(PsiBinaryExpression binaryExpression){
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        return lhs.getText() + sign.getText();
    }
}