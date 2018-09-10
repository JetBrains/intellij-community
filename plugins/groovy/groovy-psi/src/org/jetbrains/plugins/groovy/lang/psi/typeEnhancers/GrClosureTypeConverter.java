// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

public class GrClosureTypeConverter extends GrTypeConverter {
  @Nullable
  @Override
  public ConversionResult isConvertibleEx(@NotNull PsiType targetType,
                                          @NotNull PsiType actualType,
                                          @NotNull GroovyPsiElement context,
                                          @NotNull ApplicableTo currentPosition) {
    if (!TypesUtil.isClassType(targetType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE) ) return null;
    if (!TypesUtil.isClassType(actualType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE) ) return null;

    PsiClass lclass = ((PsiClassType)targetType).resolve();
    PsiClass rclass = ((PsiClassType)actualType).resolve();

    if (lclass == null || rclass == null) return null;
    PsiClassType.ClassResolveResult lresult = ((PsiClassType)targetType).resolveGenerics();
    PsiClassType.ClassResolveResult rresult = ((PsiClassType)actualType).resolveGenerics();


    if (GrGenericTypeConverter.typeParametersAgree(lclass, rclass, lresult.getSubstitutor(), rresult.getSubstitutor(), context)) return ConversionResult.OK;

    return null;
  }

  @Override
  public boolean isApplicableTo(@NotNull ApplicableTo position) {
    return position == ApplicableTo.METHOD_PARAMETER;
  }
}