/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import static com.intellij.psi.util.TypeConversionUtil.isFloatOrDoubleType;

public class GrBigDecimalConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull ApplicableTo position) {
    return position != ApplicableTo.METHOD_PARAMETER;
  }

  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@NotNull PsiType targetType,
                                          @NotNull PsiType actualType,
                                          @NotNull GroovyPsiElement context,
                                          @NotNull ApplicableTo currentPosition) {
    if (TypesUtil.isClassType(actualType, GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL) && isFloatOrDoubleType(targetType))
      return ConversionResult.OK;

    if (TypesUtil.isClassType(targetType, GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL) && TypesUtil.isNumericType(actualType))
      return ConversionResult.OK;

    if (TypesUtil.isClassType(targetType, GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER) && TypesUtil.isIntegralNumberType(actualType))
      return ConversionResult.OK;
    return null;
  }
}
