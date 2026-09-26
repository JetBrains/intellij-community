// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

public final class GrListToArrayTypeConverter extends GrTypeConverter {
  @Override
  public @Nullable ConversionResult isConvertible(@NotNull PsiType targetType,
                                                  @NotNull PsiType actualType,
                                                  @NotNull Position position,
                                                  @NotNull GroovyPsiElement context) {
    if (!(targetType instanceof PsiArrayType) || !(actualType instanceof GrTupleType)) return null;

    final PsiType lComponentType = ((PsiArrayType)targetType).getComponentType();
    for (PsiType rComponentType : ((GrTupleType)actualType).getComponentTypes()) {
      if (!TypesUtil.isAssignableByParameter(lComponentType, rComponentType, context)) return null;
    }
    return ConversionResult.OK;
  }

  @Override
  public boolean isApplicableTo(@NotNull Position position) {
    return position == Position.ASSIGNMENT;
  }
}
