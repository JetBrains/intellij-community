package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxBoundExpression;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxBoundExpressionImpl extends JavaFxBaseElementImpl implements JavaFxBoundExpression {
  public JavaFxBoundExpressionImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  @Nullable
  public JavaFxExpression getExpression() {
    final ASTNode node = getNode().findChildByType(JavaFxElementTypes.EXPRESSIONS);
    if (node == null) {
      return null;
    }
    return (JavaFxExpression) node.getPsi();
  }

  @Nullable
  @Override
  public PsiType getType() {
    final JavaFxExpression expression = getExpression();
    if (expression == null) {
      return null;
    }
    return expression.getType();
  }
}
