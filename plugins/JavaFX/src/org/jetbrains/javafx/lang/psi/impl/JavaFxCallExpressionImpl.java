package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxCallExpression;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.types.JavaFxFunctionType;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxCallExpressionImpl extends JavaFxBaseElementImpl implements JavaFxCallExpression {
  public JavaFxCallExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public JavaFxExpression getCallee() {
    //noinspection ConstantConditions
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS);
  }

  @Override
  public PsiType getType() {
    final PsiType type = getCallee().getType();
    if (type instanceof JavaFxFunctionType) {
      return ((JavaFxFunctionType)type).getReturnType();
    }
    return null;
  }
}
