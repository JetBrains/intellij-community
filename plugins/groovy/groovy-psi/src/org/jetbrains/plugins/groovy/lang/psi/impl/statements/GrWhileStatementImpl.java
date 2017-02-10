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
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @autor: ilyas
 */
public class GrWhileStatementImpl extends GroovyPsiElementImpl implements GrWhileStatement {
  public GrWhileStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitWhileStatement(this);
  }

  public String toString() {
    return "WHILE statement";
  }

  @Override
  @Nullable
  public GrExpression getCondition() {
    PsiElement lParenth = getLParenth();

    if (lParenth == null) return null;
    PsiElement afterLParenth = PsiUtil.skipWhitespacesAndComments(lParenth.getNextSibling(), true);

    if (afterLParenth instanceof GrExpression) return ((GrExpression) afterLParenth);

    return null;
  }

  @Override
  @Nullable
  public GrStatement getBody() {
    List<GrStatement> statements = new ArrayList<>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrStatement) statements.add((GrStatement)cur);
    }

    if (getCondition() == null && !statements.isEmpty()) return statements.get(0);
    if (statements.size() > 1) return statements.get(1);
    return null;
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
