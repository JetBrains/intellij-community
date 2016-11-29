/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
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

/**
 * @autor: ilyas
 */
public class GrIfStatementImpl extends GroovyPsiElementImpl implements GrIfStatement {
  public GrIfStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitIfStatement(this);
  }

  public String toString() {
    return "IF statement";
  }

  @Override
  @Nullable
  public GrExpression getCondition() {
    PsiElement lParenth = getLParenth();

    if (lParenth == null) return null;
    PsiElement afterLParenth = PsiUtil.skipWhitespacesAndComments(lParenth.getNextSibling(), true);

    if (afterLParenth instanceof GrExpression) return (GrExpression)afterLParenth;

    return null;
  }

  @Override
  @Nullable
  public GrStatement getThenBranch() {
    List<GrStatement> statements = new ArrayList<>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (GrStatement.class.isInstance(cur)) statements.add((GrStatement)cur);
    }

    if (getCondition() == null && !statements.isEmpty()) return statements.get(0);
    if (statements.size() > 1) return statements.get(1);
    return null;
  }

  @Override
  @Nullable
  public GrStatement getElseBranch() {
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
  @NotNull
  public <T extends GrStatement> T replaceThenBranch(@NotNull T newBranch) throws IncorrectOperationException {
    return PsiImplUtil.replaceBody(newBranch, getThenBranch(), getNode(), getProject());
  }

  @Override
  @NotNull
  public <T extends GrStatement> T replaceElseBranch(@NotNull T newBranch) throws IncorrectOperationException {
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

