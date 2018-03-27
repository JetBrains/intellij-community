/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static com.intellij.psi.util.TypeConversionUtil.isFloatOrDoubleType;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.ERROR;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.OK;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER;

public class GrNumberConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull ApplicableTo position) {
    return true;
  }

  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@NotNull PsiType targetType,
                                          @NotNull PsiType actualType,
                                          @NotNull GroovyPsiElement context,
                                          @NotNull ApplicableTo currentPosition) {
    if (currentPosition == ApplicableTo.METHOD_PARAMETER) {
      return methodParameterConvert(targetType, actualType);
    }
    if (PsiUtil.isCompileStatic(context)) return isCSConvertible(targetType, actualType);
    if (TypesUtil.isNumericType(targetType) && TypesUtil.isNumericType(actualType)) {
      return OK;
    }
    return null;
  }

  private static ConversionResult methodParameterConvert(PsiType targetType, PsiType actualType) {
    if (TypesUtil.isClassType(actualType, JAVA_MATH_BIG_DECIMAL))
      return isFloatOrDoubleType(targetType) ? OK : null;
    return null;
  }

  @Nullable
  private static ConversionResult isCSConvertible(@NotNull PsiType targetType, @NotNull PsiType actualType) {

    if (TypesUtil.isClassType(actualType, JAVA_MATH_BIG_DECIMAL))
      return isFloatOrDoubleType(targetType) ? OK : null;

    if (TypesUtil.isClassType(targetType, JAVA_MATH_BIG_DECIMAL))
      return TypesUtil.isNumericType(actualType) ? OK : ERROR;

    if (TypesUtil.isClassType(targetType, JAVA_MATH_BIG_INTEGER))
      return TypesUtil.isIntegralNumberType(actualType) ? OK : ERROR;

    if (TypesUtil.isClassType(actualType, JAVA_MATH_BIG_INTEGER))
      return TypesUtil.isClassType(targetType, JAVA_MATH_BIG_INTEGER) || TypesUtil.isClassType(targetType, JAVA_MATH_BIG_DECIMAL) ? OK : null;

    if (TypesUtil.isNumericType(targetType) && TypesUtil.isNumericType(actualType)) {
      return OK;
    }
    return null;
  }
}
