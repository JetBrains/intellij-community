package org.jetbrains.javafx.refactoring.surround.surrounders.expressions;

import com.intellij.codeInsight.CodeInsightBundle;
import org.jetbrains.javafx.lang.psi.JavaFxElement;
import org.jetbrains.javafx.lang.psi.JavaFxValueExpression;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxWithParenthesesSurrounder extends JavaFxExpressionSurrounder {
  @Override
  protected boolean isApplicable(final JavaFxValueExpression element) {
    return true;
  }

  @Override
  protected String generateTemplate(final JavaFxElement element) {
    return "(" + element.getText() + ")";
  }

  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.parenthesis.template");
  }
}
