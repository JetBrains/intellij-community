package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxIndexofExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxPrimitiveType;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxIndexofExpressionImpl extends JavaFxBaseElementImpl implements JavaFxIndexofExpression {
  public JavaFxIndexofExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiType getType() {
    return JavaFxPrimitiveType.INTEGER;
  }
}
