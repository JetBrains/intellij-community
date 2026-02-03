// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import static org.jetbrains.plugins.groovy.lang.psi.util.CompileStaticUtil.isCompileStatic;

public final class GrContainerConverter extends GrTypeConverter {

  @Override
  public @Nullable ConversionResult isConvertible(@NotNull PsiType lType,
                                                  @NotNull PsiType rType,
                                                  @NotNull Position position,
                                                  @NotNull GroovyPsiElement context) {
    if (isCompileStatic(context)) return null;
    if (lType instanceof PsiArrayType) {
      PsiType lComponentType = ((PsiArrayType)lType).getComponentType();
      PsiType rComponentType = ClosureParameterEnhancer.findTypeForIteration(rType, context);
      if (rComponentType != null && TypesUtil.isAssignable(lComponentType, rComponentType, context)) {
        return ConversionResult.OK;
      }
    }

    return null;
  }
}
