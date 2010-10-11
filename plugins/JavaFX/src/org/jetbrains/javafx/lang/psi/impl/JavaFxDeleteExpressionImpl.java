package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxDeleteExpression;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   18:02:52
 */
public class JavaFxDeleteExpressionImpl extends JavaFxVoidExpressionImpl implements JavaFxDeleteExpression {
  public JavaFxDeleteExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }
}
