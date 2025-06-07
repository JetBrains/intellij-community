// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Objects;

public class GrForInClauseImpl extends GroovyPsiElementImpl implements GrForInClause {

  public GrForInClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitForInClause(this);
  }

  @Override
  public String toString() {
    return "In clause";
  }

  @Override
  public GrParameter getDeclaredVariable() {
    return findChildByClass(GrParameter.class);
  }

  @Override
  public @Nullable GrExpression getIteratedExpression() {
    return findExpressionChild(this);
  }

  @Override
  public @NotNull PsiElement getDelimiter() {
    PsiElement in = findChildByType(GroovyTokenTypes.kIN);
    if (in != null) return in;

    PsiElement colon = findChildByType(GroovyTokenTypes.mCOLON);
    return Objects.requireNonNull(colon);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent != null) return true;
    GrParameter variable = getDeclaredVariable();
    if (variable == null) return true;
    return ResolveUtil.processElement(processor, variable, state);
  }
}
