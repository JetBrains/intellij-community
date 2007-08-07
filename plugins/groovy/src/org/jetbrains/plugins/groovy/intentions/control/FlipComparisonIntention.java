package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.utils.ComparisonUtils;
import org.jetbrains.plugins.groovy.intentions.base.MutablyNamedIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class FlipComparisonIntention extends MutablyNamedIntention {
  protected String getTextForElement(PsiElement element) {
    final GrBinaryExpression binaryExpression =
        (GrBinaryExpression) element;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    final String comparison = ComparisonUtils.getStringForComparison(tokenType);
    final String flippedComparison = ComparisonUtils.getFlippedComparison(tokenType);

    return GroovyIntentionsBundle.message("flip.comparison.intention.name", comparison, flippedComparison);
  }

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ComparisonPredicate();
  }

  public void processIntention(@NotNull PsiElement element)
      throws IncorrectOperationException {
    final GrBinaryExpression exp =
        (GrBinaryExpression) element;
    final IElementType tokenType = exp.getOperationTokenType();

    final GrExpression lhs = exp.getLeftOperand();
    final String lhsText = lhs.getText();

    final GrExpression rhs = exp.getRightOperand();
    final String rhsText = rhs.getText();

    final String flippedComparison = ComparisonUtils.getFlippedComparison(tokenType);

    final String newExpression =
        rhsText + flippedComparison + lhsText;
    replaceExpression(newExpression, exp);
  }

}
