// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.patterns;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;

public class GroovyBinaryExpressionPattern extends GroovyExpressionPattern<GrBinaryExpression, GroovyBinaryExpressionPattern> {
  protected GroovyBinaryExpressionPattern() {
    super(GrBinaryExpression.class);
  }

  public GroovyBinaryExpressionPattern left(final @NotNull ElementPattern pattern) {
    return with(new PatternCondition<>("left") {
      @Override
      public boolean accepts(final @NotNull GrBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getLeftOperand(), context);
      }
    });
  }

  public GroovyBinaryExpressionPattern right(final @NotNull ElementPattern pattern) {
    return with(new PatternCondition<>("right") {
      @Override
      public boolean accepts(final @NotNull GrBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getRightOperand(), context);
      }
    });
  }

  public GroovyBinaryExpressionPattern operation(final ElementPattern pattern) {
    return with(new PatternCondition<>("operation") {
      @Override
      public boolean accepts(final @NotNull GrBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getOperationTokenType(), context);
      }
    });
  }

}
