// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.psi.PsiType;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Objects;

public class GrNumericBinaryExpressionTypeCalculator implements NullableFunction<GrOperatorExpression, PsiType> {
  public static final GrNumericBinaryExpressionTypeCalculator INSTANCE = new GrNumericBinaryExpressionTypeCalculator();

  @Nullable
  @Override
  public PsiType fun(GrOperatorExpression e) {

    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(Objects.requireNonNull(e.getReference()).multiResolve(false));
    if (resolveResult.isApplicable() && !PsiUtil.isDGMMethod(resolveResult.getElement())) {
      return ResolveUtil.extractReturnTypeFromCandidate(resolveResult, e, new PsiType[]{e.getRightType()});
    }

    PsiType lType = e.getLeftType();
    PsiType rType = e.getRightType();
    if (TypesUtil.isNumericType(lType) && TypesUtil.isNumericType(rType)) {
      return inferNumericType(lType, rType, e);
    }

    return ResolveUtil.extractReturnTypeFromCandidate(resolveResult, e, new PsiType[]{rType});
  }

  @Nullable
  protected PsiType inferNumericType(@NotNull PsiType ltype, @NotNull PsiType rtype, GrOperatorExpression e) {
    return GrBinaryExpressionUtil.getDefaultNumericResultType(ltype, rtype, e);
  }
}
