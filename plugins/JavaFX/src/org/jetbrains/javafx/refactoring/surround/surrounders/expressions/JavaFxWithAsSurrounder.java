package org.jetbrains.javafx.refactoring.surround.surrounders.expressions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.lang.psi.JavaFxElement;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxParenthesizedExpression;
import org.jetbrains.javafx.lang.psi.JavaFxValueExpression;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public class JavaFxWithAsSurrounder extends JavaFxExpressionSurrounder {
  @Override
  protected boolean isApplicable(JavaFxValueExpression element) {
    return true;
  }

  @NotNull
  @Override
  protected JavaFxExpression surroundExpression(final JavaFxValueExpression element) {
    final JavaFxExpression expression = super.surroundExpression(element);
    if (expression instanceof JavaFxParenthesizedExpression) {
      return ((JavaFxParenthesizedExpression)expression).getExpression();
    }
    return expression;
  }

  @Override
  protected String generateTemplate(JavaFxElement element) {
    return "(" + element.getText() + " as )";
  }

  @Override
  public String getTemplateDescription() {
    return JavaFxBundle.message("javafx.surround.with.as.template");
  }
}
