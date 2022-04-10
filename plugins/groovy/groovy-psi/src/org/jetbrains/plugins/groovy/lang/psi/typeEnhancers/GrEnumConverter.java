// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.isEnum;

public class GrEnumConverter extends GrTypeConverter {

  @Nullable
  @Override
  public ConversionResult isConvertible(@NotNull PsiType targetType,
                                        @NotNull PsiType actualType,
                                        @NotNull Position position,
                                        @NotNull GroovyPsiElement context) {
    if (!isEnum(targetType)) return null;
    if (InheritanceUtil.isInheritor(actualType, GroovyCommonClassNames.GROOVY_LANG_GSTRING) ||
        InheritanceUtil.isInheritor(actualType, CommonClassNames.JAVA_LANG_STRING)) {
      return GroovyConfigUtils.getInstance().isVersionAtLeast(context, GroovyConfigUtils.GROOVY1_8)
             ? ConversionResult.OK
             : ConversionResult.ERROR;
    }
    return null;
  }
}
