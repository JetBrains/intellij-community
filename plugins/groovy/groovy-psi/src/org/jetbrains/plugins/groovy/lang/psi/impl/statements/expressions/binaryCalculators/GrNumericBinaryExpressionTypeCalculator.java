// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.psi.PsiType;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference;

import java.util.Objects;

import static org.jetbrains.plugins.groovy.lang.typing.DefaultMethodCallTypeCalculatorKt.getTypeFromResult;

public class GrNumericBinaryExpressionTypeCalculator implements NullableFunction<GrOperatorExpression, PsiType> {
  public static final GrNumericBinaryExpressionTypeCalculator INSTANCE = new GrNumericBinaryExpressionTypeCalculator();

  @Nullable
  @Override
  public PsiType fun(GrOperatorExpression e) {
    final GroovyCallReference operatorReference = Objects.requireNonNull(e.getReference());
    final GroovyResolveResult resolveResult = operatorReference.advancedResolve();
    if (resolveResult.isApplicable() && !PsiUtil.isDGMMethod(resolveResult.getElement())) {
      return getTypeFromResult(resolveResult, operatorReference.getArguments(), e);
    }

    PsiType lType = e.getLeftType();
    PsiType rType = e.getRightType();
    if (TypesUtil.isNumericType(lType) && TypesUtil.isNumericType(rType)) {
      return inferNumericType(lType, rType, e);
    }

    return getTypeFromResult(resolveResult, operatorReference.getArguments(), e);
  }

  @Nullable
  protected PsiType inferNumericType(@NotNull PsiType ltype, @NotNull PsiType rtype, GrOperatorExpression e) {
    return GrBinaryExpressionUtil.getDefaultNumericResultType(ltype, rtype, e);
  }
}
