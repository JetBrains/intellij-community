package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxBlockExpression;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxPsiUtil;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxVoidType;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxBlockExpressionImpl extends JavaFxBaseElementImpl implements JavaFxBlockExpression {
  public JavaFxBlockExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public JavaFxExpression[] getExpressions() {
    return JavaFxPsiUtil.nodesToPsi(getNode().getChildren(JavaFxElementTypes.EXPRESSIONS), JavaFxExpression.EMPTY_ARRAY);
  }

  @Override
  public PsiType getType() {
    final JavaFxExpression[] expressions = getExpressions();
    if (expressions.length == 0) {
      return JavaFxVoidType.INSTANCE;
    }
    return expressions[expressions.length - 1].getType();
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    for (JavaFxExpression expression : getExpressions()) {
      if (expression == lastParent) {
        break;
      }
      if (!processor.execute(expression, state)) {
        return false;
      }
    }
    return true;
  }
}
