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

/**
 * @author Max Medvedev
 */
public class GrBooleanTypeConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull ApplicableTo position) {
    return position != ApplicableTo.EXPLICIT_CAST && position != ApplicableTo.GENERIC_PARAMETER;
  }

  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@NotNull PsiType targetType,
                                          @NotNull PsiType actualType,
                                          @NotNull GroovyPsiElement context,
                                          @NotNull ApplicableTo currentPosition) {
    if (!PsiType.BOOLEAN.equals(TypesUtil.unboxPrimitiveTypeWrapper(targetType))) return null;
    if (PsiType.NULL == actualType) {
      switch (currentPosition) {
        case METHOD_PARAMETER:
          return null;
        case ASSIGNMENT:
        case RETURN_VALUE:
          return ConversionResult.OK;
        default:
          return null;
      }
    }
    return currentPosition == ApplicableTo.ASSIGNMENT || currentPosition == ApplicableTo.RETURN_VALUE
           ? ConversionResult.OK
           : null;
  }
}
