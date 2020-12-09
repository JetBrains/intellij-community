// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;

public class GrClassConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull Position position) {
    switch (position) {
      case ASSIGNMENT:
      case RETURN_VALUE:
        return true;
      default:
        return false;
    }
  }

  @Nullable
  @Override
  public ConversionResult isConvertible(@NotNull PsiType targetType,
                                        @NotNull PsiType actualType,
                                        @NotNull Position position,
                                        @NotNull GroovyPsiElement context) {
    if (!PsiTypesUtil.classNameEquals(targetType, CommonClassNames.JAVA_LANG_CLASS)) {
      return null;
    }
    if (PsiTypesUtil.classNameEquals(actualType, CommonClassNames.JAVA_LANG_CLASS)) {
      return null;
    }
    if (actualType == PsiType.NULL) return ConversionResult.OK;
    final GrLiteral literal = getLiteral(context);
    final Object value = literal == null ? null : literal.getValue();
    final String fqn = value == null ? null : value.toString();
    final PsiClass psiClass = fqn == null ? null : JavaPsiFacade.getInstance(context.getProject()).findClass(
      fqn, context.getResolveScope()
    );
    return psiClass == null ? ConversionResult.WARNING : ConversionResult.OK;
  }
}
