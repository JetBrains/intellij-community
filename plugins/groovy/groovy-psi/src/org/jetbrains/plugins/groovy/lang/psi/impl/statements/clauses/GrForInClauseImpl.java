// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public @Nullable GrParameter getIndexVariable() {
    GrParameter[] parameters = findChildrenByClass(GrParameter.class);
    if (parameters.length != 2) return null;
    return parameters[0];
  }

  @Override
  public GrParameter getDeclaredVariable() {
    GrParameter[] parameters = findChildrenByClass(GrParameter.class);
    if (parameters.length == 0) return null;
    return parameters[parameters.length - 1];
  }

  @Override
  public @Nullable GrExpression getIteratedExpression() {
    return findExpressionChild(this);
  }

  @Override
  public @Nullable PsiElement getDelimiter() {
    PsiElement in = findChildByType(GroovyTokenTypes.kIN);
    if (in != null) return in;

    return findChildByType(GroovyTokenTypes.mCOLON);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent != null) return true;
    GrParameter declaredVariable = getDeclaredVariable();
    GrParameter indexVariable = getIndexVariable();
    if (indexVariable != null && !ResolveUtil.processElement(processor, indexVariable, state)) return false;
    if (declaredVariable != null && !ResolveUtil.processElement(processor, declaredVariable, state)) return false;
    return true;
  }
}
