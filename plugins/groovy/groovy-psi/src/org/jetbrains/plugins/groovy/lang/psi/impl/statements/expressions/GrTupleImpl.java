// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitTuple(this);
  }

  @Override
  public String toString() {
    return "Tuple Assignment Expression";
  }
}
