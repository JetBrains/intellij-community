package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxForExpression;
import org.jetbrains.javafx.lang.psi.JavaFxInClause;
import org.jetbrains.javafx.lang.psi.JavaFxPsiUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxForExpressionImpl extends JavaFxBaseElementImpl implements JavaFxForExpression {
  public JavaFxForExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  public JavaFxExpression getBody() {
    final ASTNode node = getNode().findChildByType(JavaFxElementTypes.EXPRESSIONS);
    if (node != null) {
      return (JavaFxExpression)node.getPsi();
    }
    return null;
  }

  @NotNull
  @Override
  public JavaFxInClause[] getInClauses() {
    final ASTNode[] node = getNode().getChildren(TokenSet.create(JavaFxElementTypes.IN_CLAUSE));
    return JavaFxPsiUtil.nodesToPsi(node, JavaFxInClause.EMPTY_ARRAY);
  }

  @Override
  public PsiType getType() {
    final JavaFxExpression body = getBody();
    if (body == null) {
      return null;
    }
    return body.getType();
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    for (JavaFxInClause inClause : getInClauses()) {
      if (inClause != lastParent && !inClause.processDeclarations(processor, state, this, place)) {
        return false;
      }
    }
    return true;
  }
}
