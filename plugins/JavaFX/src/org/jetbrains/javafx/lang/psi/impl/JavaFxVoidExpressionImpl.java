package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxVoidType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
abstract class JavaFxVoidExpressionImpl extends JavaFxBaseElementImpl implements JavaFxExpression {
  public JavaFxVoidExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public PsiType getType() {
    return JavaFxVoidType.INSTANCE;
  }
}
