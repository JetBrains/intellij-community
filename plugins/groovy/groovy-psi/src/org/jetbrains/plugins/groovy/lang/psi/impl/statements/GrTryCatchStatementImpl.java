// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GrTryResourceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrFinallyClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

public class GrTryCatchStatementImpl extends GroovyPsiElementImpl implements GrTryCatchStatement {
  public GrTryCatchStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitTryStatement(this);
  }

  @Override
  public String toString() {
    return "Try statement";
  }

  @Override
  public @Nullable GrTryResourceList getResourceList() {
    return findChildByClass(GrTryResourceList.class);
  }

  @Override
  public @Nullable GrOpenBlock getTryBlock() {
    return findChildByClass(GrOpenBlock.class);
  }

  @Override
  public GrCatchClause @NotNull [] getCatchClauses() {
    return findChildrenByClass(GrCatchClause.class);
  }

  @Override
  public @Nullable GrFinallyClause getFinallyClause() {
    return findChildByClass(GrFinallyClause.class);
  }

  @Override
  public GrCatchClause addCatchClause(@NotNull GrCatchClause clause, @Nullable GrCatchClause anchorBefore) {
    PsiElement anchor = anchorBefore;
    if (anchor == null) {
      anchor = getTryBlock();
    }
    return (GrCatchClause)addAfter(clause, anchor);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final GrTryResourceList resourceList = getResourceList();
    if (resourceList != null && lastParent == getTryBlock()) {
      return resourceList.processDeclarations(processor, state, null, place);
    }
    return true;
  }
}
