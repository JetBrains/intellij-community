// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.impl.ConstantExpressionEvaluator;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author peter
 */
public class GroovyConstantExpressionEvaluator implements ConstantExpressionEvaluator {

  @Nullable
  public static Object evaluate(@Nullable GrExpression expression) {
    if (expression instanceof GrParenthesizedExpression) {
      return evaluate(((GrParenthesizedExpression)expression).getOperand());
    }
    if (expression instanceof GrLiteral) {
      return ((GrLiteral)expression).getValue();
    }
    if (expression instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)expression).resolve();
      if (resolved instanceof PsiField) {
        return ((PsiField)resolved).computeConstantValue();
      }
    }
    return null;
  }

  @Override
  @Nullable
  public Object computeConstantExpression(PsiElement expression, boolean throwExceptionOnOverflow) {
    if (!(expression instanceof GrExpression)) return null;
    return evaluate((GrExpression)expression);
  }

  @Override
  @Nullable
  public Object computeExpression(PsiElement expression,
                                  boolean throwExceptionOnOverflow,
                                  @Nullable PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) {
    if (!(expression instanceof GrExpression)) return null;
    return evaluate((GrExpression)expression);
  }
}
