// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessLocals;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessPatternVariables;

public class GrIfStatementImpl extends GroovyPsiElementImpl implements GrIfStatement {
  public GrIfStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitIfStatement(this);
  }

  @Override
  public String toString() {
    return "IF statement";
  }

  @Override
  public @Nullable GrExpression getCondition() {
    PsiElement lParenth = getLParenth();

    if (lParenth == null) return null;
    PsiElement afterLParenth = PsiUtil.skipWhitespacesAndComments(lParenth.getNextSibling(), true);

    if (afterLParenth instanceof GrExpression) return (GrExpression)afterLParenth;

    return null;
  }

  @Override
  public @Nullable GrStatement getThenBranch() {
    List<GrStatement> statements = new ArrayList<>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrStatement) statements.add((GrStatement)cur);
    }

    if (getCondition() == null && !statements.isEmpty()) return statements.get(0);
    if (statements.size() > 1) return statements.get(1);
    return null;
  }

  @Override
  public @Nullable GrStatement getElseBranch() {
    List<GrStatement> statements = new ArrayList<>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrStatement) statements.add((GrStatement)cur);
    }
    if (statements.size() == 3) {
      return statements.get(2);
    }

    return null;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    GrStatement elseBranch = getElseBranch();

    if (elseBranch != null && child == elseBranch.getNode()) {
      PsiElement elseKeywordElement = findChildByType(GroovyTokenTypes.kELSE);
      if (elseKeywordElement != null) {
        super.deleteChildInternal(elseKeywordElement.getNode());
      }
    }

    super.deleteChildInternal(child);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!shouldProcessLocals(processor) || !shouldProcessPatternVariables(state)) return true;
    GrExpression condition = getCondition();
    if (condition != null) {
      GrStatement thenBranch = getThenBranch();
      GrStatement elseBranch = getElseBranch();
      if (lastParent == null) {
        // Scenario happens in the situations when reference is resolved for a variable that is located in the later statements
        // but in the same scope. For example:
        // String s = null;
        // if (!(s instanceof Object obj)) { return }
        // println obj.toString()
        //
        // Such cases are not supported by Groovy.
        return true;
      }
      if (lastParent == thenBranch) {
        condition.processDeclarations(processor, PatternResolveState.WHEN_TRUE.putInto(state), null, place);
      } else if (lastParent == elseBranch) {
        return true;
      }
    }
    return true;
  }

  @Override
  public @NotNull <T extends GrStatement> T replaceThenBranch(@NotNull T newBranch) throws IncorrectOperationException {
    return PsiImplUtil.replaceBody(newBranch, getThenBranch(), getNode(), getProject());
  }

  @Override
  public @NotNull <T extends GrStatement> T replaceElseBranch(@NotNull T newBranch) throws IncorrectOperationException {
    return PsiImplUtil.replaceBody(newBranch, getElseBranch(), getNode(), getProject());
  }

  @Override
  public PsiElement getElseKeyword() {
    return findChildByType(GroovyTokenTypes.kELSE);
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

