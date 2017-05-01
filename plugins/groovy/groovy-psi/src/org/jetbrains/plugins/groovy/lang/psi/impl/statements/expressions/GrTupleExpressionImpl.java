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
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTupleExpression;

/**
 * @author ilyas
 */
public class GrTupleExpressionImpl extends GrExpressionImpl implements GrTupleExpression {

  public GrTupleExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiType getType() {
    // The reason this method exists is that tuple expression can appear in the left side of an assignment.
    // LHS should probably have its own interface with implementations: GrReferenceExpression, GrIndexProperty, GrTupleExpression,
    // while GrTupleExpression will just implement it, but not an GrExpression.
    throw new UnsupportedOperationException("Tuple expressions cannot have type");
  }

  @Override
  public int indexOf(@NotNull PsiElement element) {
    GrExpression[] children = getExpressions();
    return ArrayUtilRt.find(children, element);
  }

  @Override
  @NotNull
  public GrExpression[] getExpressions() {
    return findChildrenByClass(GrExpression.class);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTupleExpression(this);
  }

  @Override
  public String toString() {
    return "Tuple Assignment Expression";
  }
}
