// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessLocals;

/**
 * @autor: ilyas
 */
public class GrForStatementImpl extends GroovyPsiElementImpl implements GrForStatement {
  public GrForStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitForStatement(this);
  }

  public String toString() {
    return "For statement";
  }

  @Override
  @Nullable
  public GrForClause getClause() {
    return findChildByClass(GrForClause.class);
  }

  @Override
  @Nullable
  public GrStatement getBody() {
    return findChildByClass(GrStatement.class);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!shouldProcessLocals(processor)) return true;

    GrForClause forClause = getClause();
    final GrVariable varScope = PsiTreeUtil.getParentOfType(place, GrVariable.class);
    if (forClause == null) return true;
    if (lastParent == null || lastParent instanceof GrForInClause) return true;

    GrVariable var = forClause.getDeclaredVariable();
    if (var == null || var.equals(varScope)) return true;
    if (!ResolveUtil.processElement(processor, var, state)) return false;

    return true;
  }

  @Override
  public <T extends GrCondition> T replaceBody(T newBody) throws IncorrectOperationException {
    return PsiImplUtil.replaceBody(newBody, getBody(), getNode(), getProject());
  }

  @Override
  public PsiElement getRParenth() {
    return findChildByType(GroovyTokenTypes.mRPAREN);
  }

  @Override
  public PsiElement getLParenth() {
    return findChildByType(GroovyTokenTypes.mLPAREN);
  }
}
