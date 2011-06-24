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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses;

import com.intellij.lang.ASTNode;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitForInClause(this);
  }

  public String toString() {
    return "In clause";
  }

  public GrParameter getDeclaredVariable() {
    return findChildByClass(GrParameter.class);
  }

  public GrParameter[] getParameters() {
    final GrParameter declaredVariable = getDeclaredVariable();
    return declaredVariable == null ? GrParameter.EMPTY_ARRAY : new GrParameter[]{declaredVariable};
  }

  public GrParameterList getParameterList() {
    return null;
  }

  @Override
  public boolean isVarArgs() {
    throw new IncorrectOperationException("For in clause cannot have varargs");
  }

  @Nullable
  public GrExpression getIteratedExpression() {
    return findChildByClass(GrExpression.class);
  }
}
