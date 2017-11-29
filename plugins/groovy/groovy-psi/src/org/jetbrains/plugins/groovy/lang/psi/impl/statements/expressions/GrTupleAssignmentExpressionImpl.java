/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTuple;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTupleAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrAssignmentExpressionImpl.processLValue;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrAssignmentExpressionImpl.shouldProcessBindings;

public class GrTupleAssignmentExpressionImpl extends GrExpressionImpl implements GrTupleAssignmentExpression {

  public GrTupleAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public PsiType getType() {
    GrExpression rValue = getRValue();
    return rValue == null ? null : rValue.getType();
  }

  @NotNull
  @Override
  public GrTuple getLValue() {
    return findNotNullChildByClass(GrTuple.class);
  }

  @Nullable
  @Override
  public GrExpression getRValue() {
    return findChildByClass(GrExpression.class);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTupleAssignmentExpression(this);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!shouldProcessBindings(this, processor, lastParent, place)) return true;
    final GroovyFileImpl file = ((GroovyFileImpl)getParent());
    for (GrExpression expression : getLValue().getExpressions()) {
      if (!processLValue(processor, state, place, file, expression)) return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "Tuple assignment";
  }
}
