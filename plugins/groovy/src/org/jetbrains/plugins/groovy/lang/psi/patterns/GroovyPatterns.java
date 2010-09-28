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

import com.intellij.patterns.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

public class GroovyPatterns extends PsiJavaPatterns {

  public static GroovyElementPattern groovyElement() {
    return new GroovyElementPattern.Capture<GroovyPsiElement>(GroovyPsiElement.class);
  }

  public static GroovyBinaryExpressionPattern groovyBinaryExpression() {
    return new GroovyBinaryExpressionPattern();
  }

  public static GroovyElementPattern.Capture<GrLiteral> groovyLiteralExpression() {
    return groovyLiteralExpression(null);
  }

  public static GroovyElementPattern.Capture<GrLiteral> groovyLiteralExpression(final ElementPattern value) {
    return new GroovyElementPattern.Capture<GrLiteral>(new InitialPatternCondition<GrLiteral>(GrLiteral.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof GrLiteral
               && (value == null || value.accepts(((GrLiteral)o).getValue(), context));
      }
    });
  }

  public static GroovyMethodCallPattern methodCall(final ElementPattern<? extends String> names, final String className) {
    return new GroovyMethodCallPattern().with(new PatternCondition<GrCallExpression>("methodCall") {
      public boolean accepts(@NotNull GrCallExpression callExpression, ProcessingContext context) {
        if (!(callExpression instanceof GrMethodCall)) return false;

        GrExpression expression = ((GrMethodCall)callExpression).getInvokedExpression();
        if (!(expression instanceof GrReferenceExpression)) return false;

        GrReferenceExpression refExpression = (GrReferenceExpression)expression;

        if (!names.accepts(refExpression.getName(), context)) return false;

        PsiElement element = refExpression.resolve();

        if (!(element instanceof PsiMethod)) return false;

        PsiClass containingClass = ((PsiMethod)element).getContainingClass();

        if (containingClass == null) return false;

        return InheritanceUtil.isInheritor(containingClass, className);
      }
    });
  }

  public static GroovyMethodCallPattern methodCall(final ElementPattern<? extends PsiMethod> method) {
    return new GroovyMethodCallPattern().with(new PatternCondition<GrCallExpression>("methodCall") {
      public boolean accepts(@NotNull GrCallExpression callExpression, ProcessingContext context) {
        final GroovyResolveResult[] results = callExpression.getCallVariants(null);
        for (GroovyResolveResult result : results) {
          if (method.getCondition().accepts(result.getElement(), context)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public static PsiFilePattern.Capture<GroovyFile> groovyScript() {
    return new PsiFilePattern.Capture<GroovyFile>(new InitialPatternCondition<GroovyFile>(GroovyFile.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return o instanceof GroovyFileBase && ((GroovyFileBase)o).isScript();
      }
    });
  }
}
