// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference;

import java.util.List;
import java.util.Objects;

import static org.jetbrains.plugins.groovy.lang.typing.DefaultMethodCallTypeCalculatorKt.getTypeFromResult;

public class GrNumericBinaryExpressionTypeCalculator implements NullableFunction<GrOperatorExpression, PsiType> {
  public static final GrNumericBinaryExpressionTypeCalculator INSTANCE = new GrNumericBinaryExpressionTypeCalculator();

  @Override
  public @Nullable PsiType fun(GrOperatorExpression e) {
    final GroovyCallReference operatorReference = Objects.requireNonNull(e.getReference());
    final GroovyResolveResult resolveResult = operatorReference.advancedResolve();
    return getTypeByResult(e.getLeftType(), e.getRightType(), operatorReference.getArguments(), resolveResult, e);
  }

  public @Nullable PsiType getTypeByResult(PsiType leftType, PsiType rightType, List<Argument> arguments, GroovyResolveResult resolveResult, GrExpression context) {
    if (resolveResult.isApplicable() && !PsiUtil.isDGMMethod(resolveResult.getElement())) {
      return getTypeFromResult(resolveResult, arguments, context);
    }

    if (TypesUtil.isNumericType(leftType) && TypesUtil.isNumericType(rightType)) {
      return inferNumericType(leftType, rightType, context);
    }

    return getTypeFromResult(resolveResult, arguments, context);
  }

  protected @Nullable PsiType inferNumericType(@NotNull PsiType ltype, @NotNull PsiType rtype, PsiElement e) {
    return GrBinaryExpressionUtil.getDefaultNumericResultType(ltype, rtype, e);
  }
}
