package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxElementVisitor;
import org.jetbrains.javafx.lang.psi.JavaFxReturnExpression;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxReturnExpressionImpl extends JavaFxVoidExpressionImpl implements JavaFxReturnExpression {
  public JavaFxReturnExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitReturnExpression(this);
  }
}
