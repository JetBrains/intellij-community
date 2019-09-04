// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

public class GrStringConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull Position position) {
    return position == Position.ASSIGNMENT || position == Position.RETURN_VALUE || position == Position.EXPLICIT_CAST;
  }

  @Nullable
  @Override
  public ConversionResult isConvertible(@NotNull PsiType lType,
                                        @NotNull PsiType rType,
                                        @NotNull Position position,
                                        @NotNull GroovyPsiElement context) {
    if (!TypesUtil.isClassType(lType, CommonClassNames.JAVA_LANG_STRING)) return null;
    if (position == Position.EXPLICIT_CAST) {
      return TypesUtil.isClassType(rType, GroovyCommonClassNames.GROOVY_LANG_GSTRING)
             ? ConversionResult.OK
             : null;
    }
    return ConversionResult.OK;
  }
}
