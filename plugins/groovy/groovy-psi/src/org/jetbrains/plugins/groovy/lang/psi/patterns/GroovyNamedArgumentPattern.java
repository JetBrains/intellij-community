// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.patterns;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.StringPattern;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyNamedArgumentPattern extends GroovyElementPattern<GrNamedArgument, GroovyNamedArgumentPattern> {

  public GroovyNamedArgumentPattern() {
    super(GrNamedArgument.class);
  }

  public GroovyNamedArgumentPattern withLabel(final @NotNull String label) {
    return with(new PatternCondition<>("left") {
      @Override
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        return label.equals(namedArgument.getLabelName());
      }
    });
  }

  public GroovyNamedArgumentPattern withLabel(final @NotNull StringPattern labelPattern) {
    return with(new PatternCondition<>("left") {
      @Override
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        return labelPattern.accepts(namedArgument.getLabelName(), context);
      }
    });
  }

  public GroovyNamedArgumentPattern withExpression(final @NotNull ElementPattern pattern) {
    return with(new PatternCondition<>("left") {
      @Override
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        return pattern.accepts(namedArgument.getExpression(), context);
      }
    });
  }

  public GroovyNamedArgumentPattern isParameterOfMethodCall(final @Nullable ElementPattern<? extends GrCall> methodCall) {
    return with(new PatternCondition<>("left") {
      @Override
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        GrCall call = PsiUtil.getCallByNamedParameter(namedArgument);

        return call != null && (methodCall == null || methodCall.accepts(call, context));
      }
    });
  }

}
