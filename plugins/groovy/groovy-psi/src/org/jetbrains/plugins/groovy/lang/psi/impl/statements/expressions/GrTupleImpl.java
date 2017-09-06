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
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTuple;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTupleAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

public class GrTupleImpl extends GroovyPsiElementImpl implements GrTuple {

  public GrTupleImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public int indexOf(@NotNull PsiElement element) {
    GrExpression[] children = getExpressions();
    return ArrayUtilRt.find(children, element);
  }

  @NotNull
  @Override
  public GrReferenceExpression[] getExpressions() {
    return findChildrenByClass(GrReferenceExpression.class);
  }

  @Nullable
  @Override
  public GrTupleAssignmentExpression getParent() {
    PsiElement parent = super.getParent();
    assert parent == null || parent instanceof GrTupleAssignmentExpression : parent.getClass().getName();
    return (GrTupleAssignmentExpression)parent;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTuple(this);
  }

  @Override
  public String toString() {
    return "Tuple Assignment Expression";
  }
}
