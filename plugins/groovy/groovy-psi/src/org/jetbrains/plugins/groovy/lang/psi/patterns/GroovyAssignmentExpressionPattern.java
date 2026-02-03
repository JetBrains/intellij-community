// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.patterns;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;

public class GroovyAssignmentExpressionPattern extends GroovyExpressionPattern<GrAssignmentExpression, GroovyAssignmentExpressionPattern> {
  protected GroovyAssignmentExpressionPattern() {
    super(GrAssignmentExpression.class);
  }

  public GroovyAssignmentExpressionPattern left(final @NotNull ElementPattern pattern) {
    return with(new PatternCondition<>("left") {
      @Override
      public boolean accepts(final @NotNull GrAssignmentExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getLValue(), context);
      }
    });
  }

  public GroovyAssignmentExpressionPattern right(final @NotNull ElementPattern pattern) {
    return with(new PatternCondition<>("right") {
      @Override
      public boolean accepts(final @NotNull GrAssignmentExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getRValue(), context);
      }
    });
  }

  public GroovyAssignmentExpressionPattern operation(final IElementType pattern) {
    return with(new PatternCondition<>("operation") {
      @Override
      public boolean accepts(final @NotNull GrAssignmentExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern == psiBinaryExpression.getOperationTokenType();
      }
    });
  }

}
