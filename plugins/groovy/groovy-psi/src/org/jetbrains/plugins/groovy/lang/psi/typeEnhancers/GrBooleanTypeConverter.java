// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author Max Medvedev
 */
public final class GrBooleanTypeConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull Position position) {
    return position != Position.EXPLICIT_CAST && position != Position.GENERIC_PARAMETER;
  }

  @Override
  public @Nullable ConversionResult isConvertible(@NotNull PsiType targetType,
                                                  @NotNull PsiType actualType,
                                                  @NotNull Position position,
                                                  @NotNull GroovyPsiElement context) {
    if (!PsiTypes.booleanType().equals(TypesUtil.unboxPrimitiveTypeWrapper(targetType))) return null;
    return switch (position) {
      case ASSIGNMENT, RETURN_VALUE -> ConversionResult.OK;
      default -> null;
    };
  }
}
