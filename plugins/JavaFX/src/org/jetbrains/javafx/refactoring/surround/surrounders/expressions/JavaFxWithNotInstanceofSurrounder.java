package org.jetbrains.javafx.refactoring.surround.surrounders.expressions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.lang.psi.*;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public class JavaFxWithNotInstanceofSurrounder extends JavaFxExpressionSurrounder {
  @Override
  protected boolean isApplicable(JavaFxValueExpression element) {
    return true;
  }

  @NotNull
  @Override
  protected JavaFxExpression surroundExpression(JavaFxValueExpression element) {
    final JavaFxExpression notExpression = super.surroundExpression(element);
    final JavaFxParenthesizedExpression parenthesizedExpression =
      (JavaFxParenthesizedExpression)((JavaFxUnaryExpression)notExpression).getOperand();
    return parenthesizedExpression.getExpression();
  }

  @Override
  protected String generateTemplate(JavaFxElement element) {
    return "not (" + element.getText() + " instanceof )";
  }

  @Override
  public String getTemplateDescription() {
    return JavaFxBundle.message("javafx.surround.with.not.instanceof.template");
  }
}
