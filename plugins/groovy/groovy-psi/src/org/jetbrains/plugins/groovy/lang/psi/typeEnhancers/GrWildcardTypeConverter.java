// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesKt;


public class GrWildcardTypeConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull Position position) {
    return switch (position) {
      case METHOD_PARAMETER, GENERIC_PARAMETER, ASSIGNMENT, RETURN_VALUE -> true;
      default -> false;
    };
  }

  @Override
  @Nullable
  public ConversionResult isConvertible(@NotNull PsiType ltype,
                                        @NotNull PsiType rtype,
                                        @NotNull Position position,
                                        @NotNull GroovyPsiElement context) {
    PsiType lBound = TypesKt.promoteLowerBoundWildcard(ltype, context);
    PsiType rBound = TypesKt.promoteLowerBoundWildcard(rtype, context);
    if (lBound == null || rBound == null) return null;

    if (TypeConversionUtil.isAssignable(lBound, rBound)) return ConversionResult.OK;
    return null;
  }
}
