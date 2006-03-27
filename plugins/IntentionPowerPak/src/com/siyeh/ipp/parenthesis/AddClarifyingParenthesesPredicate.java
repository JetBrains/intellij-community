/**
 * (c) 2004 Carp Technologies BV
 * Hengelosestraat 705, 7521PA Enschede
 * Created: Mar 22, 2006, 1:30:36 AM
 */
package com.siyeh.ipp.parenthesis;

import com.siyeh.ipp.base.PsiElementPredicate;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.tree.IElementType;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class AddClarifyingParenthesesPredicate implements PsiElementPredicate {

	public boolean satisfiedBy(PsiElement element) {
		if (!(element instanceof PsiBinaryExpression)) {
			return false;
		}
		final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
		final PsiJavaToken sign = binaryExpression.getOperationSign();
		final IElementType tokenType = sign.getTokenType();
		final PsiElement parent = element.getParent();
		if (parent instanceof PsiBinaryExpression) {
			final PsiBinaryExpression parentBinaryExpression = (PsiBinaryExpression)parent;
			final PsiJavaToken parentSign = parentBinaryExpression.getOperationSign();
			final IElementType parentTokenType = parentSign.getTokenType();
			return !tokenType.equals(parentTokenType);
		}
		final PsiExpression lhs = binaryExpression.getLOperand();
		final PsiExpression rhs = binaryExpression.getROperand();
		if (lhs instanceof PsiBinaryExpression) {
			final PsiBinaryExpression lhsBinaryExpression = (PsiBinaryExpression)lhs;
			final PsiJavaToken lhsSign = lhsBinaryExpression.getOperationSign();
			final IElementType lhsTokenType = lhsSign.getTokenType();
			if (!tokenType.equals(lhsTokenType)) {
				return true;
			}
		}
		if (rhs instanceof PsiBinaryExpression) {
			final PsiBinaryExpression rhsBinaryExpression = (PsiBinaryExpression)rhs;
			final PsiJavaToken rhsSign = rhsBinaryExpression.getOperationSign();
			final IElementType rhsTokenType = rhsSign.getTokenType();
			if (!tokenType.equals(rhsTokenType)) {
				return true;
			}
		}
		return false;
	}
}