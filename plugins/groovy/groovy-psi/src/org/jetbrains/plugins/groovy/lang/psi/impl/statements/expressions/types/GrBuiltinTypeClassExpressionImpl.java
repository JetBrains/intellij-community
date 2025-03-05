// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBuiltinTypeClassExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

public class GrBuiltinTypeClassExpressionImpl extends GrExpressionImpl implements GrBuiltinTypeClassExpression {

  public GrBuiltinTypeClassExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitBuiltinTypeClassExpression(this);
  }

  @Override
  public String toString() {
    return "builtin type class expression";
  }

  @Override
  public @NotNull PsiPrimitiveType getPrimitiveType() {
    return TypesUtil.getPrimitiveTypeByText(getText());
  }
}
