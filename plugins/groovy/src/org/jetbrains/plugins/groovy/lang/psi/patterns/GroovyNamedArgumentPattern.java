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

/**
 * @author Sergey Evdokimov
 */
public class GroovyNamedArgumentPattern extends GroovyElementPattern<GrNamedArgument, GroovyNamedArgumentPattern> {

  public GroovyNamedArgumentPattern() {
    super(GrNamedArgument.class);
  }

  public GroovyNamedArgumentPattern withLabel(@NotNull final String label) {
    return with(new PatternCondition<GrNamedArgument>("left") {
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        return label.equals(namedArgument.getLabelName());
      }
    });
  }

  public GroovyNamedArgumentPattern withLabel(@NotNull final StringPattern labelPattern) {
    return with(new PatternCondition<GrNamedArgument>("left") {
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        return labelPattern.getCondition().accepts(namedArgument.getLabelName(), context);
      }
    });
  }

  public GroovyNamedArgumentPattern withExpression(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<GrNamedArgument>("left") {
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        return pattern.getCondition().accepts(namedArgument.getExpression(), context);
      }
    });
  }

  public GroovyNamedArgumentPattern isParameterOfMethodCall(@Nullable final ElementPattern<? extends GrCall> methodCall) {
    return with(new PatternCondition<GrNamedArgument>("left") {
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        GrCall call = PsiUtil.getMethodByNamedParameter(namedArgument);

        return call != null && (methodCall == null || methodCall.accepts(call, context));
      }
    });
  }

}
