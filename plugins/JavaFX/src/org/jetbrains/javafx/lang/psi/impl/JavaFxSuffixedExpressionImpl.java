package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxSuffixedExpression;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxSuffixedExpressionImpl extends JavaFxBaseElementImpl implements JavaFxSuffixedExpression {
  public JavaFxSuffixedExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  // TODO:
  @NotNull
  @Override
  public IElementType getOperator() {
    return null;
  }

  @NotNull
  @Override
  public JavaFxExpression getOperand() {
    //noinspection ConstantConditions
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS);
  }

  @Override
  public PsiType getType() {
    return getOperand().getType();
  }
}
