// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators.GrBinaryExpressionTypeCalculators;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference;

public abstract class GrOperatorExpressionImpl extends GrExpressionImpl implements GrOperatorExpression {

  protected GrOperatorExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public abstract @Nullable GroovyCallReference getReference();

  @Override
  public @Nullable PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, GrBinaryExpressionTypeCalculators::computeType);
  }
}
