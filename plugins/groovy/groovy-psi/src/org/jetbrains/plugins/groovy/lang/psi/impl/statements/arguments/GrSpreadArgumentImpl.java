// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * @author Max Medvedev
 */
public class GrSpreadArgumentImpl extends GroovyPsiElementImpl implements GrSpreadArgument {
  public GrSpreadArgumentImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public GrExpression getArgument() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  public PsiType getType() {
    return getArgument().getType();
  }

  @Override
  public PsiType getNominalType() {
    return getType();
  }

  @Override
  public GrExpression replaceWithExpression(@NotNull GrExpression expression, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, expression, removeUnnecessaryParentheses);
  }

  @Override
  public String toString() {
    return "Spread argument";
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitSpreadArgument(this);
  }
}
