// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.typing.EmptyListLiteralType;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_SET;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.resolvesTo;
import static org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.ASSIGNMENT;
import static org.jetbrains.plugins.groovy.lang.psi.util.CompileStaticUtil.isCompileStatic;

/**
 * @author Maxim.Medvedev
 */
public class GrContainerTypeConverter extends GrTypeConverter {
  @Nullable
  @Override
  public ConversionResult isConvertible(@NotNull PsiType targetType,
                                        @NotNull PsiType actualType,
                                        @NotNull Position position,
                                        @NotNull GroovyPsiElement context) {
    if (position == ASSIGNMENT && resolvesTo(targetType, JAVA_UTIL_SET) && actualType instanceof EmptyListLiteralType) {
      return ConversionResult.OK;
    }
    if (isCompileStatic(context)) {
      return isCSConvertible(targetType, actualType, context);
    }
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
      if (((PsiArrayType)targetType).getComponentType() instanceof PsiPrimitiveType != ((PsiArrayType)actualType).getComponentType() instanceof PsiPrimitiveType) {
        // groovy 3.0.13 disallows boxing in array components
        return null;
      }
      return TypesUtil.isAssignableByParameter(((PsiArrayType)targetType).getComponentType(), ((PsiArrayType)actualType).getComponentType(),
                                               context) ? ConversionResult.OK : ConversionResult.ERROR;
    }
    return null;
  }

  @Override
  public boolean isApplicableTo(@NotNull Position position) {
    return position != Position.METHOD_PARAMETER;
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
