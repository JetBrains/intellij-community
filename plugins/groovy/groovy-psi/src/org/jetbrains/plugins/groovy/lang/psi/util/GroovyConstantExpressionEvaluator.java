// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.impl.ConstantExpressionEvaluator;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.skipParenthesesDown;

/**
 * @author peter
 */
public class GroovyConstantExpressionEvaluator implements ConstantExpressionEvaluator {

  @Nullable
  public static Object evaluate(@Nullable GrExpression expression) {
    GrExpression operand = skipParenthesesDown(expression);
    if (operand instanceof GrLiteral) {
      return ((GrLiteral)operand).getValue();
    }
    if (operand instanceof GrUnaryExpression) {
      var evaluatedOperand = evaluate(((GrUnaryExpression)operand).getOperand());
      if (((GrUnaryExpression)operand).getOperationTokenType() == GroovyElementTypes.T_MINUS && evaluatedOperand instanceof Number) {
        if (evaluatedOperand instanceof Integer) {
          return -(Integer)evaluatedOperand;
        } else if (evaluatedOperand instanceof Byte) {
          return -(Byte)evaluatedOperand;
        } else if (evaluatedOperand instanceof Short) {
          return -(Short)evaluatedOperand;
        } else if (evaluatedOperand instanceof Long) {
          return -(Long)evaluatedOperand;
        } else if (evaluatedOperand instanceof Double) {
          return -(Double)evaluatedOperand;
        } else if (evaluatedOperand instanceof Float) {
          return -(Float)evaluatedOperand;
        }
      }
    }
    if (expression instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)expression).resolve();
      if (resolved instanceof PsiField) {
        return ((PsiField)resolved).computeConstantValue();
      }
    }
    return null;
  }

  @Internal
  public static Object evaluateNoResolve(@Nullable GrExpression expression) {
    GrExpression operand = skipParenthesesDown(expression);
    if (operand instanceof GrLiteral) {
      return ((GrLiteral)operand).getValue();
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
