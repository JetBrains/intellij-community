package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxBreakExpression;
import org.jetbrains.javafx.lang.psi.JavaFxElementVisitor;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   18:14:42
 */
public class JavaFxBreakExpressionImpl extends JavaFxVoidExpressionImpl implements JavaFxBreakExpression {
  public JavaFxBreakExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitBreakExpression(this);
  }
}
