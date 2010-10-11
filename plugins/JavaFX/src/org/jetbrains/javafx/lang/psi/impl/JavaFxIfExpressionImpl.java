package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxIfExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxVoidType;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxIfExpressionImpl extends JavaFxBaseElementImpl implements JavaFxIfExpression {
  public JavaFxIfExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public JavaFxExpression getIfBranch() {
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS, 1);
  }

  @Nullable
  @Override
  public JavaFxExpression getElseBranch() {
     return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS, 2);
  }

  @Override
  public PsiType getType() {
    final JavaFxExpression ifBranch = getIfBranch();
    if (ifBranch == null) {
      return null;
    }
    final JavaFxExpression elseBranch = getElseBranch();
    final PsiType type = ifBranch.getType();
    if (elseBranch == null || type != elseBranch.getType()) {
      return JavaFxVoidType.INSTANCE;
    }
    return type;
  }
}
