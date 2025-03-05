// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public GroovyElementPattern(final @NotNull InitialPatternCondition<T> condition) {
    super(condition);
  }

  @Override
  public @NotNull Self methodCallParameter(final int index, final ElementPattern<? extends PsiMethod> methodPattern) {
    final PsiNamePatternCondition nameCondition = ContainerUtil.findInstance(methodPattern.getCondition().getConditions(), PsiNamePatternCondition.class);

    return with(new PatternCondition<>("methodCallParameter") {
      @Override
      public boolean accepts(final @NotNull T literal, final ProcessingContext context) {
        final PsiElement parent = literal.getParent();
        if (parent instanceof GrArgumentList psiExpressionList) {
          if (!(literal instanceof GrExpression)) return false;

          if (psiExpressionList.getExpressionArgumentIndex((GrExpression)literal) != index) return false;

          final PsiElement element = psiExpressionList.getParent();
          if (element instanceof GrCall) {
            final GroovyPsiElement expression =
              element instanceof GrMethodCall ? ((GrMethodCall)element).getInvokedExpression() :
              element instanceof GrNewExpression ? ((GrNewExpression)element).getReferenceElement() :
              null;


            if (expression instanceof GrReferenceElement ref) {

              if (nameCondition != null && "withName".equals(nameCondition.getDebugMethodName())) {
                final String methodName = ref.getReferenceName();
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

  public @NotNull Self regExpOperatorArgument() {
    return with(new PatternCondition<>("regExpOperatorArg") {
      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        PsiElement parent = t.getParent();
        return parent instanceof GrUnaryExpression && ((GrUnaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mBNOT ||
               parent instanceof GrBinaryExpression &&
               t == ((GrBinaryExpression)parent).getRightOperand() &&
               ((GrBinaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mREGEX_FIND ||
               parent instanceof GrBinaryExpression &&
               t == ((GrBinaryExpression)parent).getRightOperand() &&
               ((GrBinaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mREGEX_MATCH;
      }
    });
  }



  public static class Capture<T extends GroovyPsiElement> extends GroovyElementPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(final @NotNull InitialPatternCondition<T> condition) {
      super(condition);
    }
  }

}
