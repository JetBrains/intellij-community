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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.psi.PsiType;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * Created by Max Medvedev on 12/20/13
 */
public class GrNumericBinaryExpressionTypeCalculator implements NullableFunction<GrBinaryFacade, PsiType> {
  public static final GrNumericBinaryExpressionTypeCalculator INSTANCE = new GrNumericBinaryExpressionTypeCalculator();

  @Nullable
  @Override
  public PsiType fun(GrBinaryFacade e) {

    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(e.multiResolve(false));
    if (resolveResult.isApplicable() && !PsiUtil.isDGMMethod(resolveResult.getElement())) {
      return ResolveUtil.extractReturnTypeFromCandidate(resolveResult, e.getPsiElement(), new PsiType[]{GrBinaryExpressionUtil.getRightType(e)});
    }

    PsiType lType = GrBinaryExpressionUtil.getLeftType(e);
    PsiType rType = GrBinaryExpressionUtil.getRightType(e);
    if (TypesUtil.isNumericType(lType) && TypesUtil.isNumericType(rType)) {
      assert lType != null;
      assert rType != null;
      return inferNumericType(lType, rType, e);
    }

    return ResolveUtil.extractReturnTypeFromCandidate(resolveResult, e.getPsiElement(), new PsiType[]{rType});
  }

  @Nullable
  protected PsiType inferNumericType(@NotNull PsiType ltype, @NotNull PsiType rtype, GrBinaryFacade e) {
    return GrBinaryExpressionUtil.getDefaultNumericResultType(ltype, rtype, e);
  }
}
