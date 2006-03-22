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

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class AddClarifyingParenthesesPredicate implements PsiElementPredicate {

	public boolean satisfiedBy(PsiElement element) {
		if (!(element instanceof PsiBinaryExpression)) {
			return false;
		}
		if (element.getParent() instanceof PsiBinaryExpression) {
			return true;
		}
		final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
		final PsiExpression lhs = binaryExpression.getLOperand();
		if (lhs instanceof PsiBinaryExpression) {
			return true;
		}
		final PsiExpression rhs = binaryExpression.getROperand();
		return rhs instanceof PsiBinaryExpression;
	}
}