/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @autor: ilyas
 */
public class GrIfStatementImpl extends GroovyPsiElementImpl implements GrIfStatement {
  public GrIfStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitIfStatement(this);
  }

  public String toString() {
    return "IF statement";
  }

  @Nullable
  public GrExpression getCondition() {
    PsiElement lParenth = getLParenth();

    if (lParenth == null) return null;
    PsiElement afterLParen = lParenth.getNextSibling();

    if (afterLParen instanceof GrExpression) return (GrExpression)afterLParen;

    return null;
  }

  @Nullable
  public GrStatement getThenBranch() {
    GrStatement[] statements = findChildrenByClass(GrStatement.class);

    if (getCondition() == null && statements.length > 0) return statements[0];
    else if (statements.length > 1 && (statements[1] instanceof GrStatement)) return statements[1];

    return null;
  }

  @Nullable
  public GrStatement getElseBranch() {
    GrStatement[] statements = findChildrenByClass(GrStatement.class);
    if (statements.length == 3 && (statements[2] instanceof GrStatement)) {
      return statements[2];
    }

    return null;
  }

  public <T extends GrStatement> T replaceThenBranch(T newBranch) throws IncorrectOperationException {
    return PsiImplUtil.replaceBody(newBranch, getThenBranch(), getNode(), getProject());
  }

  public <T extends GrStatement> T replaceElseBranch(T newBranch) throws IncorrectOperationException {
    return PsiImplUtil.replaceBody(newBranch, getElseBranch(), getNode(), getProject());
  }

  public PsiElement getElseKeyword() {
    return findChildByType(GroovyTokenTypes.kELSE);
  }

  public PsiElement getRParenth() {
    return findChildByType(GroovyTokenTypes.mRPAREN);
  }

  public PsiElement getLParenth() {
    return findChildByType(GroovyTokenTypes.mLPAREN);
  }
}

