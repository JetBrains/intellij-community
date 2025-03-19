// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

  @Override
  public GrReferenceExpression @NotNull [] getExpressions() {
    return findChildrenByClass(GrReferenceExpression.class);
  }

  @Override
  public @Nullable GrTupleAssignmentExpression getParent() {
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
