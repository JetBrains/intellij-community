// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.CompileStaticUtil;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.*;

public final class GrNullVoidConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull Position position) {
    return switch (position) {
      case RETURN_VALUE, ASSIGNMENT, METHOD_PARAMETER -> true;
      default -> false;
    };
  }

  @Override
  public @Nullable ConversionResult isConvertible(@NotNull PsiType targetType,
                                                  @NotNull PsiType actualType,
                                                  @NotNull Position position,
                                                  @NotNull GroovyPsiElement context) {

    final PsiClassType objectType = TypesUtil.getJavaLangObject(context);
    boolean isCompileStatic = CompileStaticUtil.isCompileStatic(context);
    if (position == Position.RETURN_VALUE) {
      if (targetType.equals(objectType) && PsiTypes.voidType().equals(actualType)) {
        return OK;
      }
    }

    if (PsiTypes.voidType().equals(actualType)) {
      switch (position) {
        case RETURN_VALUE -> {
          // We can return void values from method. But it's very suspicious.
          return WARNING;
        }
        case ASSIGNMENT -> {
          if (targetType.equals(PsiTypes.booleanType())) return null;
          if (targetType.equals(PsiType.getJavaLangString(context.getManager(), context.getResolveScope()))) return WARNING;
          return isCompileStatic ? ERROR : WARNING;
        }
        default -> {
        }
      }
    }
    else if (actualType == PsiTypes.nullType()) {
      if (position == Position.RETURN_VALUE) {
        // We can return null from method returning primitive type, but runtime error will occur.
        if (targetType instanceof PsiPrimitiveType) return WARNING;
      }
      else {
        return targetType instanceof PsiPrimitiveType ? ERROR : OK;
      }
    }
    return null;
  }
}
