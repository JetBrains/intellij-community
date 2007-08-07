package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.utils.BoolUtils;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class FlipConditionalIntention extends Intention {


  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ConditionalPredicate();
  }

  public void processIntention(@NotNull PsiElement element)
      throws IncorrectOperationException {
    final GrConditionalExpression exp =
        (GrConditionalExpression) element;

    final GrExpression condition = exp.getCondition();
    final GrExpression elseExpression = exp.getElseBranch();
    final GrExpression thenExpression = exp.getThenBranch();
    assert elseExpression != null;
    assert thenExpression != null;
    final String newExpression =
        BoolUtils.getNegatedExpressionText(condition) + '?' +
            elseExpression.getText() +
            ':' +
            thenExpression.getText();
    replaceExpression(newExpression, exp);
  }

}
