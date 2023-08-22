// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrPropertySelection;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;

public class GrPropertySelectionImpl extends GrExpressionImpl implements GrPropertySelection {
  private static final Logger LOG = Logger.getInstance(GrPropertySelectionImpl.class);

  public GrPropertySelectionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitPropertySelection(this);
  }

  @Override
  public String toString() {
    return "Property selection";
  }

  @NotNull
  @Override
  public GrExpression getQualifier() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @NotNull
  @Override
  public PsiElement getReferenceNameElement() {
    final PsiElement last = getLastChild();
    LOG.assertTrue(last != null);
    return last;
  }

  @Nullable
  @Override
  public PsiType getType() {
    return null;
  }
}
