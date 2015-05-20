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

import com.intellij.patterns.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;

public class GroovyElementPattern<T extends GroovyPsiElement,Self extends GroovyElementPattern<T,Self>> extends PsiJavaElementPattern<T,Self> {
  public GroovyElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  public GroovyElementPattern(@NotNull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  @Override
  public Self methodCallParameter(final int index, final ElementPattern<? extends PsiMethod> methodPattern) {
    final PsiNamePatternCondition nameCondition = ContainerUtil.findInstance(methodPattern.getCondition().getConditions(), PsiNamePatternCondition.class);

    return with(new PatternCondition<T>("methodCallParameter") {
      @Override
      public boolean accepts(@NotNull final T literal, final ProcessingContext context) {
        final PsiElement parent = literal.getParent();
        if (parent instanceof GrArgumentList) {
          if (!(literal instanceof GrExpression)) return false;

          final GrArgumentList psiExpressionList = (GrArgumentList)parent;
          if (psiExpressionList.getExpressionArgumentIndex((GrExpression)literal) != index) return false;

          final PsiElement element = psiExpressionList.getParent();
          if (element instanceof GrCall) {
            final GroovyPsiElement expression =
              element instanceof GrMethodCall ? ((GrMethodCall)element).getInvokedExpression() :
              element instanceof GrNewExpression? ((GrNewExpression)element).getReferenceElement() :
              null;


            if (expression instanceof GrReferenceElement) {
              final GrReferenceElement ref = (GrReferenceElement)expression;

              if (nameCondition != null && "withName".equals(nameCondition.getDebugMethodName())) {
                final String methodName = ref.getReferenceName();
                //noinspection unchecked
                if (methodName != null && !nameCondition.getNamePattern().accepts(methodName, context)) {
                  return false;
                }
              }

              for (GroovyResolveResult result : ref.multiResolve(false)) {
                final PsiElement psiElement = result.getElement();
                if (methodPattern.accepts(psiElement, context)) {
                  return true;
                }
              }
            }
          }
        }
        return false;
      }
    });
  }

  public Self regExpOperatorArgument() {
    return with(new PatternCondition<T>("regExpOperatorArg") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        PsiElement parent = t.getParent();
        return parent instanceof GrUnaryExpression && ((GrUnaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mBNOT ||
               parent instanceof GrBinaryExpression && t == ((GrBinaryExpression)parent).getRightOperand() && ((GrBinaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mREGEX_FIND ||
               parent instanceof GrBinaryExpression && t == ((GrBinaryExpression)parent).getRightOperand() && ((GrBinaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mREGEX_MATCH;
      }
    });
  }



  public static class Capture<T extends GroovyPsiElement> extends GroovyElementPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(@NotNull final InitialPatternCondition<T> condition) {
      super(condition);
    }
  }

}
