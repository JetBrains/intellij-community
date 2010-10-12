package org.jetbrains.javafx.refactoring.surround.surrounders.expressions;

import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.lang.psi.JavaFxElement;
import org.jetbrains.javafx.lang.psi.JavaFxValueExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxPrimitiveType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxWithNotSurrounder extends JavaFxExpressionSurrounder {
  @Override
  protected boolean isApplicable(final JavaFxValueExpression element) {
    return JavaFxPrimitiveType.BOOLEAN.equals(element.getType());
  }

  @Override
  protected String generateTemplate(final JavaFxElement element) {
    return "not (" + element.getText() + ")";
  }

  @Override
  public String getTemplateDescription() {
    return JavaFxBundle.message("javafx.surround.with.not.template");
  }
}
