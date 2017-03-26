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

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.isEnum;

public class GrNullVoidConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull ApplicableTo position) {
    switch (position) {
      case RETURN_VALUE:
      case ASSIGNMENT:
      case METHOD_PARAMETER:
        return true;
      default:
        return false;
    }
  }

  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@NotNull PsiType targetType,
                                          @NotNull PsiType actualType,
                                          @NotNull GroovyPsiElement context,
                                          @NotNull ApplicableTo currentPosition) {

    final PsiClassType objectType = TypesUtil.getJavaLangObject(context);

    if (currentPosition == ApplicableTo.RETURN_VALUE) {
      if (targetType.equals(objectType) && PsiType.VOID.equals(actualType)) {
        return ConversionResult.OK;                                           // can return void from Object
      }
    }

    if (PsiType.VOID.equals(actualType)) {
      switch (currentPosition) {
        case RETURN_VALUE: {
          // we can return void values from method returning enum
          if (isEnum(targetType)) return ConversionResult.OK;
          // we can return null or void from method returning primitive type, but runtime error will occur.
          if (targetType instanceof PsiPrimitiveType) return ConversionResult.WARNING;
        }
        break;
        case ASSIGNMENT: {
          if (targetType.equals(PsiType.BOOLEAN)) return null;
          return targetType instanceof PsiPrimitiveType || isEnum(targetType) ? ConversionResult.ERROR : ConversionResult.OK;
        }
        default:
          break;
      }
    }
    else if (actualType == PsiType.NULL) {
      switch (currentPosition) {
        case RETURN_VALUE:
          // we can return null or void from method returning primitive type, but runtime error will occur.
          if (targetType instanceof PsiPrimitiveType) return ConversionResult.WARNING;
          break;
        default:
          return targetType instanceof PsiPrimitiveType ? ConversionResult.ERROR : ConversionResult.OK;
      }
    }
    return null;
  }
}
