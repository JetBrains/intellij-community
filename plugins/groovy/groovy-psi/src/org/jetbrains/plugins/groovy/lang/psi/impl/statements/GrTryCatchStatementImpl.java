// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrFinallyClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GrTryCatchStatementImpl extends GroovyPsiElementImpl implements GrTryCatchStatement {
  public GrTryCatchStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitTryStatement(this);
  }

  public String toString() {
    return "Try statement";
  }

  @Override
  @Nullable
  public GrOpenBlock getTryBlock() {
    return findChildByClass(GrOpenBlock.class);
  }

  @Override
  @NotNull
  public GrCatchClause[] getCatchClauses() {
    List<GrCatchClause> result = new ArrayList<>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrCatchClause) result.add((GrCatchClause)cur);
    }
    return result.toArray(new GrCatchClause[0]);
  }

  @Override
  public GrFinallyClause getFinallyClause() {
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
}
