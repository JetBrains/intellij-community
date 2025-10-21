// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrWhileStatementBase;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind;

public class GrWhileStatementImpl extends GrWhileStatementBase implements GrWhileStatement {

  public GrWhileStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitWhileStatement(this);
  }

  @Override
  public String toString() {
    return "WHILE statement";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    GroovyResolveKind.Hint elementClassHint = processor.getHint(GroovyResolveKind.HINT_KEY);
    if (elementClassHint != null && !elementClassHint.shouldProcess(GroovyResolveKind.VARIABLE)) return true;
    GrCondition condition = getCondition();
    if (condition != null && lastParent == getBody()) {
      return condition.processDeclarations(processor, PatternResolveState.WHEN_TRUE.putInto(state), null, place);
    }
    return true;
  }

  @Override
  public @Nullable GrStatement getBody() {
    PsiElement rParenth = getRParenth();
    if (rParenth == null) return null;

    PsiElement afterRParenth = PsiUtil.skipWhitespacesAndComments(rParenth.getNextSibling(), true);
    return afterRParenth instanceof GrStatement ? (GrStatement)afterRParenth : null;
  }
}
