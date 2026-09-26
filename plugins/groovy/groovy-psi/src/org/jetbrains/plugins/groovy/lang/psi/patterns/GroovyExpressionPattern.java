// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.patterns;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;

public class GroovyExpressionPattern<T extends GrExpression, Self extends GroovyExpressionPattern<T,Self>> extends GroovyElementPattern<T,Self> {
  protected GroovyExpressionPattern(final Class<T> aClass) {
    super(aClass);
  }

  public @NotNull Self ofType(final @NotNull ElementPattern pattern) {
    return with(new PatternCondition<>("ofType") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        return pattern.accepts(t.getType(), context);
      }
    });
  }

  public @NotNull Self skipParentheses(final ElementPattern<? extends GrExpression> expressionPattern) {
    return with(new PatternCondition<>("skipParentheses") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        GrExpression expression = t;
        while (expression instanceof GrParenthesizedExpression) {
          expression = ((GrParenthesizedExpression)expression).getOperand();
        }
        return expressionPattern.accepts(expression, context);
      }
    });
  }

  public static class Capture<T extends GrExpression> extends GroovyExpressionPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

  }
}
