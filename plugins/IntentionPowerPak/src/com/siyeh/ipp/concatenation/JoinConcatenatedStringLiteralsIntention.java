package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class JoinConcatenatedStringLiteralsIntention extends Intention{
    protected PsiElementPredicate getElementPredicate(){
        return new StringConcatPredicate();
    }

    public String getText(){
		return "Join concatenated String literals";
    }

    public String getFamilyName(){
        return "Join Concatenated String Literals";
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) element.getParent();
		final PsiBinaryExpression copy = (PsiBinaryExpression)binaryExpression.copy();
		final PsiExpression lhs = copy.getLOperand();
		String newExpression = "";
		if (lhs instanceof PsiBinaryExpression) {
			final PsiBinaryExpression lhsBinaryExpression = (PsiBinaryExpression)lhs;
			newExpression += getLeftSideText(lhsBinaryExpression);
			final PsiExpression rightSide = lhsBinaryExpression.getROperand();
			lhs.replace(rightSide);
        }
		newExpression += '"' + computeConstantStringExpression(copy) + '"';
		replaceExpression( newExpression, binaryExpression);
	}

	/**
	 * handles the specified expression as if it was part of a string expression (even if it's
	 * of another type) and computes a constant string expression from it.
	 */
	private static String computeConstantStringExpression(PsiBinaryExpression expression) {
		final PsiManager manager = expression.getManager();
		final PsiConstantEvaluationHelper constantEvaluationHelper =
		        manager.getConstantEvaluationHelper();
		final PsiExpression lhs = expression.getLOperand();
		final Object lhsConstant = constantEvaluationHelper.computeConstantExpression(lhs);
		final String lhsText = lhsConstant.toString();
		String result;
		if (lhsText.charAt(0) == '\'' || lhsText.charAt(0) == '"') {
			result = lhsText.substring(1, lhsText.length() - 1);
        } else{
			result = lhsText;
        }
		final PsiExpression rhs = expression.getROperand();
		final Object rhsConstant = constantEvaluationHelper.computeConstantExpression(rhs);
		final String rhsText = rhsConstant.toString();
		if (rhsText.charAt(0) == '\'' || rhsText.charAt(0) == '"') {
			result += rhsText.substring(1, rhsText.length() - 1);
        } else{
			result += rhsText;
        }
		return result;
    }

	private static String getLeftSideText(PsiBinaryExpression binaryExpression) {
		final PsiExpression lhs = binaryExpression.getLOperand();
		final PsiJavaToken sign = binaryExpression.getOperationSign();
		return lhs.getText() + sign.getText();
    }
}
