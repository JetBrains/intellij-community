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
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

public class GroovyMethodCallPattern extends GroovyExpressionPattern<GrCallExpression, GroovyMethodCallPattern> {
  GroovyMethodCallPattern() {
    super(GrCallExpression.class);
  }

  public GroovyMethodCallPattern withArguments(final ElementPattern<? extends GrExpression>... arguments) {
    return with(new PatternCondition<GrCallExpression>("withArguments") {
      @Override
      public boolean accepts(@NotNull GrCallExpression callExpression, ProcessingContext context) {
        final GrArgumentList argumentList = callExpression.getArgumentList();
        if (argumentList == null) return false;
        final GrExpression[] actualArguments = argumentList.getExpressionArguments();
        if (arguments.length != actualArguments.length) {
          return false;
        }
        for (int i = 0; i < actualArguments.length; i++) {
          if (!arguments[i].accepts(actualArguments[i], context)) {
            return false;
          }
        }
        return true;
      }
    });
  }

  public GroovyMethodCallPattern withMethodName(@NotNull String methodName) {
    return withMethodName(StandardPatterns.string().equalTo(methodName));
  }

  public GroovyMethodCallPattern withMethodName(final ElementPattern<? extends String> methodName) {
    return with(new PatternCondition<GrCallExpression>("withMethodName") {
      @Override
      public boolean accepts(@NotNull GrCallExpression callExpression, ProcessingContext context) {
        if (!(callExpression instanceof GrMethodCall)) return false;

        GrExpression expression = ((GrMethodCall)callExpression).getInvokedExpression();
        if (!(expression instanceof GrReferenceExpression)) return false;

        GrReferenceExpression refExpression = (GrReferenceExpression)expression;

        return methodName.accepts(refExpression.getReferenceName(), context);
      }
    });
  }

  public GroovyMethodCallPattern withMethod(final ElementPattern<? extends PsiMethod> methodPattern) {
    return with(new PatternCondition<GrCallExpression>("methodCall") {
      @Override
      public boolean accepts(@NotNull GrCallExpression callExpression, ProcessingContext context) {
        for (GroovyResolveResult result : callExpression.getCallVariants(null)) {
          if (methodPattern.accepts(result.getElement(), context)) {
            return true;
          }
        }
        return false;
      }
    });
  }
}
