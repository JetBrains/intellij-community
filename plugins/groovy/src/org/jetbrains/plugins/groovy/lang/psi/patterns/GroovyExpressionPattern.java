/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;

public class GroovyExpressionPattern<T extends GrExpression, Self extends GroovyExpressionPattern<T,Self>> extends GroovyElementPattern<T,Self> {
  protected GroovyExpressionPattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self ofType(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<T>("ofType") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return pattern.getCondition().accepts(t.getType(), context);
      }
    });
  }

  public Self skipParentheses(final ElementPattern<? extends GrExpression> expressionPattern) {
    return with(new PatternCondition<T>("skipParentheses") {
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
