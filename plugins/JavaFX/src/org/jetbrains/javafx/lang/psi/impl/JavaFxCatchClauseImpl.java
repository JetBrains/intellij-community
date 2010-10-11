package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxCatchClause;
import org.jetbrains.javafx.lang.psi.JavaFxParameter;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxCatchClauseImpl extends JavaFxBaseElementImpl implements JavaFxCatchClause {
  public JavaFxCatchClauseImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public JavaFxParameter getParameter() {
    final ASTNode child = getNode().findChildByType(JavaFxElementTypes.FORMAL_PARAMETER);
    if (child == null) {
      return null;
    }
    return (JavaFxParameter)child.getPsi();
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    final JavaFxParameter parameter = getParameter();
    if (parameter != null && parameter != lastParent && !processor.execute(parameter, state)) {
      return false;
    }
    return true;
  }
}
