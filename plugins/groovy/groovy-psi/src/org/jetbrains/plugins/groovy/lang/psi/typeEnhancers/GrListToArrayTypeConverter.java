// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

public class GrListToArrayTypeConverter extends GrTypeConverter {
  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@NotNull PsiType targetType,
                                          @NotNull PsiType actualType,
                                          @NotNull GroovyPsiElement context,
                                          @NotNull ApplicableTo currentPosition) {
    if (!(targetType instanceof PsiArrayType) || !(actualType instanceof GrTupleType)) return null;

    final PsiType lComponentType = ((PsiArrayType)targetType).getComponentType();
    PsiType[] types = ((GrTupleType)actualType).getComponentTypes();
    for (PsiType rComponentType: types) {
      if (!TypesUtil.isAssignableByParameter(lComponentType, rComponentType, context)) return null;
    }
    return ConversionResult.OK;
  }

  @Override
  public boolean isApplicableTo(@NotNull ApplicableTo position) {
    return position == ApplicableTo.ASSIGNMENT;
  }
}
