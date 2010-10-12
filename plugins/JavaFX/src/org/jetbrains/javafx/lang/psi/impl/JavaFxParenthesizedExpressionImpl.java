package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxParenthesizedExpression;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxParenthesizedExpressionImpl extends JavaFxBaseElementImpl implements JavaFxParenthesizedExpression {
  public JavaFxParenthesizedExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public JavaFxExpression getExpression() {
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS);
  }

  @Override
  public PsiType getType() {
    final JavaFxExpression expression = getExpression();
    if (expression == null) {
      return null;
    }
    return expression.getType();
  }
}
