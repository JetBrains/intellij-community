// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isCompileStatic;

/**
 * @author Maxim.Medvedev
 */
public class GrContainerTypeConverter extends GrTypeConverter {
  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@NotNull PsiType targetType,
                                          @NotNull PsiType actualType,
                                          @NotNull GroovyPsiElement context,
                                          @NotNull ApplicableTo currentPosition) {
    if (isCompileStatic(context)) return isCSConvertible(targetType, actualType, context);
    if (!isCollectionOrArray(targetType) || !isCollectionOrArray(actualType)) return null;


    final PsiType lComponentType = extractComponentType(targetType);
    final PsiType rComponentType = extractComponentType(actualType);

    if (lComponentType == null || rComponentType == null) return ConversionResult.OK;
    if (TypesUtil.isAssignableByParameter(lComponentType, rComponentType, context)) return ConversionResult.OK;
    return null;
  }

  @Nullable
  private static ConversionResult isCSConvertible(@NotNull PsiType targetType,
                                                  @NotNull PsiType actualType,
                                                  @NotNull GroovyPsiElement context) {
    if (targetType instanceof PsiArrayType && actualType instanceof PsiArrayType) {
      return TypesUtil.isAssignableByParameter(((PsiArrayType)targetType).getComponentType(), ((PsiArrayType)actualType).getComponentType(),
                                               context) ? ConversionResult.OK : ConversionResult.ERROR;
    }
    return null;
  }

  @Override
  public boolean isApplicableTo(@NotNull ApplicableTo position) {
    return position != ApplicableTo.METHOD_PARAMETER;
  }

  @Nullable
  private static PsiType extractComponentType(PsiType type) {
    if (type instanceof PsiArrayType) return ((PsiArrayType)type).getComponentType();
    return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_COLLECTION, 0, false);
  }

  private static boolean isCollectionOrArray(PsiType type) {
    return type instanceof PsiArrayType || InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION);
  }
}
