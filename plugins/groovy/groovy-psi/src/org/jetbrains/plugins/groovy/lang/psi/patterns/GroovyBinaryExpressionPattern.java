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
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;

public class GroovyBinaryExpressionPattern extends GroovyExpressionPattern<GrBinaryExpression, GroovyBinaryExpressionPattern> {
  protected GroovyBinaryExpressionPattern() {
    super(GrBinaryExpression.class);
  }

  public GroovyBinaryExpressionPattern left(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<>("left") {
      @Override
      public boolean accepts(@NotNull final GrBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getLeftOperand(), context);
      }
    });
  }

  public GroovyBinaryExpressionPattern right(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<>("right") {
      @Override
      public boolean accepts(@NotNull final GrBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getRightOperand(), context);
      }
    });
  }

  public GroovyBinaryExpressionPattern operation(final ElementPattern pattern) {
    return with(new PatternCondition<>("operation") {
      @Override
      public boolean accepts(@NotNull final GrBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getOperationTokenType(), context);
      }
    });
  }

}
