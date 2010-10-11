package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxInClause;
import org.jetbrains.javafx.lang.psi.JavaFxParameter;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:40:15
 */
public class JavaFxInClauseImpl extends JavaFxBaseElementImpl implements JavaFxInClause {
  public JavaFxInClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public JavaFxParameter getParameter() {
    return (JavaFxParameter)childToPsi(JavaFxElementTypes.FORMAL_PARAMETER);
  }

  @Override
  @Nullable
  public JavaFxExpression getIteratedExpression() {
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS);
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    return processor.execute(getParameter(), state);
  }
}
