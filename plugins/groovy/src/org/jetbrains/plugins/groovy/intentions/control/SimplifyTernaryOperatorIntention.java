// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator;

/**
 * @author Niels Harremoes
 * @author Oscar Toernroth
 */
public final class SimplifyTernaryOperatorIntention extends GrPsiUpdateIntention {

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof GrConditionalExpression condExp)) {
      throw new IncorrectOperationException("Not invoked on a conditional");
    }
    GrExpression thenBranch = condExp.getThenBranch();
    GrExpression elseBranch = condExp.getElseBranch();

    Object thenVal = GroovyConstantExpressionEvaluator.evaluate(thenBranch);
    if (Boolean.TRUE.equals(thenVal) && elseBranch != null) {
      // aaa ? true : bbb -> aaa || bbb
      GrExpression conditionExp = condExp.getCondition();

      String conditionExpText = getStringToPutIntoOrExpression(conditionExp);
      String elseExpText = getStringToPutIntoOrExpression(elseBranch);
      String newExp = conditionExpText + "||" + elseExpText;
      manageReplace(updater, condExp, conditionExpText, newExp);
      return;
    }

    Object elseVal = GroovyConstantExpressionEvaluator.evaluate(elseBranch);
    if (Boolean.FALSE.equals(elseVal) && thenBranch != null) {
      // aaa ? bbb : false -> aaa && bbb
      GrExpression conditionExp = condExp.getCondition();

      String conditionExpText = getStringToPutIntoAndExpression(conditionExp);
      String thenExpText = getStringToPutIntoAndExpression(thenBranch);


      String newExp = conditionExpText + "&&" + thenExpText;
      manageReplace(updater, condExp, conditionExpText, newExp);
    }
  }

  private static void manageReplace(ModPsiUpdater updater,
                                    GrConditionalExpression condExp,
                                    String conditionExpText, String newExp) {
    int caretOffset = conditionExpText.length() + 2; // after operation sign

    GrExpression expressionFromText = GroovyPsiElementFactory.getInstance(condExp.getProject())
      .createExpressionFromText(newExp, condExp .getContext());

    expressionFromText = (GrExpression)condExp.replace(expressionFromText);

    updater.moveCaretTo(expressionFromText.getTextOffset() + caretOffset); // just past operation sign
  }

  /**
   * Convert an expression into something which can be put inside ( a && b )
   * Wrap in parenthesis, if necessary
   *
   * @return a string representing the expression
   */
  private static @NotNull String getStringToPutIntoAndExpression(GrExpression expression) {
    String expressionText = expression.getText();
    if (ParenthesesUtils.AND_PRECEDENCE < ParenthesesUtils.getPrecedence(expression)) {
      expressionText = "(" + expressionText + ")";
    }
    return expressionText;
  }

  private static @NotNull String getStringToPutIntoOrExpression(GrExpression expression) {
    String expressionText = expression.getText();
    if (ParenthesesUtils.OR_PRECEDENCE < ParenthesesUtils.getPrecedence(expression)) {
      expressionText = "(" + expressionText + ")";
    }
    return expressionText;
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return element -> {
      if (!(element instanceof GrConditionalExpression condExp)) {
        return false;
      }

      PsiType condType = condExp.getType();
      if (condType == null || !PsiTypes.booleanType().isConvertibleFrom(condType)) {
        return false;
      }

      GrExpression thenBranch = condExp.getThenBranch();
      GrExpression elseBranch = condExp.getElseBranch();

      Object thenVal = GroovyConstantExpressionEvaluator.evaluate(thenBranch);
      if (Boolean.TRUE.equals(thenVal) && elseBranch != null) {
        return true;
      }

      Object elseVal = GroovyConstantExpressionEvaluator.evaluate(elseBranch);
      if (thenBranch != null && Boolean.FALSE.equals(elseVal)) {
        return true;
      }

      return false;
    };
  }
}
