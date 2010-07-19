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
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @autor: ilyas
 */
public class GrForStatementImpl extends GroovyPsiElementImpl implements GrForStatement {
  public GrForStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitForStatement(this);
  }

  public String toString() {
    return "For statement";
  }

  public GrForClause getClause() {
    return findChildByClass(GrForClause.class);
  }

  public GrStatement getBody() {
    return findChildByClass(GrStatement.class);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    GrForClause forClause = getClause();
    final GrVariable varScope = PsiTreeUtil.getParentOfType(place, GrVariable.class);
    if (forClause != null && lastParent != null && !(lastParent instanceof GrForInClause)) {
      GrVariable[] vars = forClause.getDeclaredVariables();
      for (final GrVariable var : vars) {
        if (var.equals(varScope)) break;
        if (!ResolveUtil.processElement(processor, var, state)) return false;
      }
    }

    return true;
  }

  public GrCondition replaceBody(GrCondition newBody) throws IncorrectOperationException {
    if (getBody() == null ||
            newBody == null) {
      throw new IncorrectOperationException();
    }
    ASTNode oldBodyNode = getBody().getNode();
    if (oldBodyNode.getTreePrev() != null &&
            GroovyTokenTypes.mNLS.equals(oldBodyNode.getTreePrev().getElementType())) {
      ASTNode whiteNode = GroovyPsiElementFactory.getInstance(getProject()).createWhiteSpace().getNode();
      getNode().replaceChild(oldBodyNode.getTreePrev(), whiteNode);
    }
    this.getNode().replaceChild(oldBodyNode, newBody.getNode());
    ASTNode newNode = newBody.getNode();
    if (!(newNode.getPsi() instanceof GrCondition)) {
      throw new IncorrectOperationException();
    }
    return (GrCondition) newNode.getPsi();
  }

  public PsiElement getRParenth() {
    return findChildByType(GroovyTokenTypes.mRPAREN);
  }

  public PsiElement getLParenth() {
    return findChildByType(GroovyTokenTypes.mLPAREN);
  }
}
