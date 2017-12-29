/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.*;

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
    boolean isCompileStatic = PsiUtil.isCompileStatic(context);
    if (currentPosition == ApplicableTo.RETURN_VALUE) {
      if (targetType.equals(objectType) && PsiType.VOID.equals(actualType)) {
        return OK;
      }
    }

    if (PsiType.VOID.equals(actualType)) {
      switch (currentPosition) {
        case RETURN_VALUE: {
          // We can return void values from method. But it's very suspicious.
          return WARNING;
        }
        case ASSIGNMENT: {
          if (targetType.equals(PsiType.BOOLEAN)) return null;
          if (targetType.equals(PsiType.getJavaLangString(context.getManager(), context.getResolveScope()))) return WARNING;
          return isCompileStatic ? ERROR : WARNING;
        }
        default:
          break;
      }
    }
    else if (actualType == PsiType.NULL) {
      switch (currentPosition) {
        case RETURN_VALUE:
          // We can return null from method returning primitive type, but runtime error will occur.
          if (targetType instanceof PsiPrimitiveType) return WARNING;
          break;
        default:
          return targetType instanceof PsiPrimitiveType ? ERROR : OK;
      }
    }
    return null;
  }
}
