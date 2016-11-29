/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils;

import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.BoolUtils.isNegation;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ComparisonUtils.isComparison;

public final class MethodCallData {
  private final PsiElement backingElement;

  private boolean isNegated;
  private GrExpression base;
  private GrExpression[] arguments;
  private IElementType comparison;

  @Nullable
  public static MethodCallData create(@NotNull PsiElement backingElement) {
    return Builder.build(backingElement);
  }

  private MethodCallData(@NotNull PsiElement backingElement) {
    this.backingElement = backingElement;
  }

  @NotNull
  public PsiElement getBackingElement() {
    return backingElement;
  }

  public boolean isNegated() {
    return isNegated;
  }

  @NotNull
  public String getBase() {
    return base.getText();
  }

  @Nullable
  public String getArgument(int i) {
    return (i >= arguments.length) ? null
                                   : arguments[i].getText();
  }

  @Nullable
  public IElementType getComparison() {
    return comparison;
  }

  private static final class Builder {
    @Nullable
    public static MethodCallData build(@NotNull PsiElement backingElement) {
      MethodCallData result = new MethodCallData(backingElement);

      PsiElement element = backingElement;
      element = handleNegation(result, element);
      element = handleComparison(result, element);
      element = handleMethodCall(result, element);
      return (element == null) ? null
                               : result;
    }

    private static PsiElement handleNegation(MethodCallData result, @Nullable PsiElement element) {
      if (element == null) return null;

      result.isNegated = isNegation(element);

      if (result.isNegated) element = ((GrUnaryExpression)element).getOperand();

      return element;
    }

    private static PsiElement handleComparison(MethodCallData result, @Nullable PsiElement element) {
      if (element == null) return null;

      if (isComparison(element)) {
        GrBinaryExpression relationalExpression = (GrBinaryExpression)element;

        element = relationalExpression.getLeftOperand();
        result.comparison = relationalExpression.getOperationTokenType();
      }
      return element;
    }

    @Nullable
    private static PsiElement handleMethodCall(MethodCallData result, PsiElement element) {
      if (!(element instanceof GrMethodCall)) return null;

      GrMethodCall methodCall = (GrMethodCall)element;

      GrReferenceExpression invokedExpression = (GrReferenceExpression)methodCall.getInvokedExpression();
      GrExpression qualifierExpression = invokedExpression.getQualifierExpression();
      if (qualifierExpression == null) return null;

      result.base = addParenthesesIfNeeded(qualifierExpression);
      result.arguments = addParenthesesIfNeeded(methodCall.getExpressionArguments());

      return element;
    }

    private static GrExpression addParenthesesIfNeeded(@NotNull GrExpression expression) {
      return needsParentheses(expression) ? createParenthesizedExpr(expression)
                                          : expression;
    }

    private static boolean needsParentheses(@NotNull GrExpression expression) {
      return ParenthesesUtils.getPrecedence(expression) >= ParenthesesUtils.TYPE_CAST_PRECEDENCE;
    }

    private static GrExpression[] addParenthesesIfNeeded(GrExpression[] expressions) {
      GrExpression[] results = new GrExpression[expressions.length];
      for (int i = 0; i < results.length; i++) {
        results[i] = addParenthesesIfNeeded(expressions[i]);
      }
      return results;
    }

    public static GrExpression createParenthesizedExpr(@NotNull GrExpression expression) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expression.getProject());
      return factory.createParenthesizedExpr(expression);
    }
  }
}


