/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      @Override
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        return label.equals(namedArgument.getLabelName());
      }
    });
  }

  public GroovyNamedArgumentPattern withLabel(@NotNull final StringPattern labelPattern) {
    return with(new PatternCondition<GrNamedArgument>("left") {
      @Override
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        return labelPattern.accepts(namedArgument.getLabelName(), context);
      }
    });
  }

  public GroovyNamedArgumentPattern withExpression(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<GrNamedArgument>("left") {
      @Override
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        return pattern.accepts(namedArgument.getExpression(), context);
      }
    });
  }

  public GroovyNamedArgumentPattern isParameterOfMethodCall(@Nullable final ElementPattern<? extends GrCall> methodCall) {
    return with(new PatternCondition<GrNamedArgument>("left") {
      @Override
      public boolean accepts(@NotNull GrNamedArgument namedArgument, final ProcessingContext context) {
        GrCall call = PsiUtil.getCallByNamedParameter(namedArgument);

        return call != null && (methodCall == null || methodCall.accepts(call, context));
      }
    });
  }

}
