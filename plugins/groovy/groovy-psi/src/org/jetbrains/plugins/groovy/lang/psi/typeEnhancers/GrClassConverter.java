// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;

public final class GrClassConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull Position position) {
    return switch (position) {
      case ASSIGNMENT, RETURN_VALUE -> true;
      default -> false;
    };
  }

  @Override
  public @Nullable ConversionResult isConvertible(@NotNull PsiType targetType,
                                                  @NotNull PsiType actualType,
                                                  @NotNull Position position,
                                                  @NotNull GroovyPsiElement context) {
    if (!PsiTypesUtil.classNameEquals(targetType, CommonClassNames.JAVA_LANG_CLASS)) {
      return null;
    }
    if (PsiTypesUtil.classNameEquals(actualType, CommonClassNames.JAVA_LANG_CLASS)) {
      return null;
    }
    if (actualType == PsiTypes.nullType()) return ConversionResult.OK;
    final GrLiteral literal = getLiteral(context);
    final Object value = literal == null ? null : literal.getValue();
    final String fqn = value == null ? null : value.toString();
    final PsiClass psiClass = fqn == null ? null : JavaPsiFacade.getInstance(context.getProject()).findClass(
      fqn, context.getResolveScope()
    );
    return psiClass == null ? ConversionResult.WARNING : ConversionResult.OK;
  }
}
