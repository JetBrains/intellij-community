package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxWhileExpression;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   18:14:42
 */
public class JavaFxWhileExpressionImpl extends JavaFxVoidExpressionImpl implements JavaFxWhileExpression {
  public JavaFxWhileExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  public JavaFxExpression getCondition() {
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS, 0); // TODO: Value expressions
  }

  @Nullable
  public JavaFxExpression getBody() {
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS, 1);
  }
}
