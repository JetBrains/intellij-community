/**
 * (c) 2004 Carp Technologies BV
 * Hengelosestraat 705, 7521PA Enschede
 * Created: Mar 22, 2006, 1:31:39 AM
 */
package com.siyeh.ipp.parenthesis;

import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class AddClarifyingParenthesesIntention extends Intention {

	@NotNull protected
	PsiElementPredicate getElementPredicate() {
		return new AddClarifyingParenthesesPredicate();
	}

	protected void processIntention(@NotNull PsiElement element)
			throws IncorrectOperationException {
		final PsiExpression expression = getTopLevelExpression(element);
		if (expression == null) {
			return;
		}
		final String newExpression = createReplacementText(expression);
		replaceExpression(newExpression, expression);
	}

	@Nullable
	private static PsiExpression getTopLevelExpression(PsiElement element) {
		if (!(element instanceof PsiExpression)) {
			return null;
		}
		PsiExpression result = (PsiExpression)element;
		PsiElement parent = result.getParent();
		while (parent instanceof PsiBinaryExpression ||
				parent instanceof PsiParenthesizedExpression) {
			result = (PsiExpression)parent;
			parent = result.getParent();
		}
		return result;
	}

	private static String createReplacementText(PsiExpression element) {
		if (element instanceof PsiBinaryExpression) {
			final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
			final PsiExpression lhs = binaryExpression.getLOperand();
			final PsiJavaToken operationSign = binaryExpression.getOperationSign();
			final PsiExpression rhs = binaryExpression.getROperand();
			final PsiElement parent = element.getParent();
			final String signText = operationSign.getText();
			if (parent instanceof PsiBinaryExpression) {
				final PsiBinaryExpression parentBinaryExpression = (PsiBinaryExpression)parent;
				final PsiJavaToken parentOperationSign = parentBinaryExpression.getOperationSign();
				if (signText.equals(parentOperationSign.getText())) {
					return createReplacementText(lhs) + signText + createReplacementText(rhs);
				}
				return '(' + createReplacementText(lhs) + signText +
				       createReplacementText(rhs) + ')';
			} else {
				return createReplacementText(lhs) + signText +
				       createReplacementText(rhs);
			}
		} else if (element instanceof PsiParenthesizedExpression) {
			final PsiParenthesizedExpression parenthesizedExpression =
					(PsiParenthesizedExpression)element;
			final PsiExpression expression = parenthesizedExpression.getExpression();
			return '(' + createReplacementText(expression) + ')';
		}
		return element.getText();
	}
}