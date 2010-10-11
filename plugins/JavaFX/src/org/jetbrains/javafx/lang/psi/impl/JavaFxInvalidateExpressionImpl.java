package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxInvalidateExpression;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxInvalidateExpressionImpl extends JavaFxVoidExpressionImpl implements JavaFxInvalidateExpression {
  public JavaFxInvalidateExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }
}
