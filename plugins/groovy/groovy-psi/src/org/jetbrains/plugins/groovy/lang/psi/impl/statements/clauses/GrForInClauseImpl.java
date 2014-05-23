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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrForInClauseImpl extends GroovyPsiElementImpl implements GrForInClause, GrParametersOwner {

  public GrForInClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitForInClause(this);
  }

  public String toString() {
    return "In clause";
  }

  @Override
  public GrParameter getDeclaredVariable() {
    return findChildByClass(GrParameter.class);
  }

  @Override
  public GrParameter[] getParameters() {
    final GrParameter declaredVariable = getDeclaredVariable();
    return declaredVariable == null ? GrParameter.EMPTY_ARRAY : new GrParameter[]{declaredVariable};
  }

  @Override
  public GrParameterList getParameterList() {
    return null;
  }

  @Override
  public boolean isVarArgs() {
    throw new IncorrectOperationException("For in clause cannot have varargs");
  }

  @Override
  @Nullable
  public GrExpression getIteratedExpression() {
    return findExpressionChild(this);
  }

  @NotNull
  @Override
  public PsiElement getDelimiter() {
    PsiElement in = findChildByType(GroovyTokenTypes.kIN);
    if (in != null) return in;

    PsiElement colon = findChildByType(GroovyTokenTypes.mCOLON);
    return ObjectUtils.assertNotNull(colon);
  }
}
