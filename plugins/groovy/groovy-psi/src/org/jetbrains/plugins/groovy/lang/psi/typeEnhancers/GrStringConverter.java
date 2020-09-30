// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.isClassType;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_GSTRING;

public class GrStringConverter extends GrTypeConverter {

  @Override
  public boolean isApplicableTo(@NotNull Position position) {
    return position == Position.ASSIGNMENT ||
           position == Position.RETURN_VALUE ||
           position == Position.EXPLICIT_CAST ||
           position == Position.METHOD_PARAMETER;
  }

  @Nullable
  @Override
  public ConversionResult isConvertible(@NotNull PsiType lType,
                                        @NotNull PsiType rType,
                                        @NotNull Position position,
                                        @NotNull GroovyPsiElement context) {
    if (!isClassType(lType, JAVA_LANG_STRING)) return null;
    if (position == Position.EXPLICIT_CAST || position == Position.METHOD_PARAMETER) {
      return isClassType(rType, GROOVY_LANG_GSTRING)
             ? ConversionResult.OK
             : null;
    }
    return ConversionResult.OK;
  }

  @Nullable
  @Override
  public Collection<ConstraintFormula> reduceTypeConstraint(@NotNull PsiType leftType,
                                                            @NotNull PsiType rightType,
                                                            @NotNull Position position,
                                                            @NotNull PsiElement context) {
    if (position == Position.METHOD_PARAMETER &&
        isClassType(leftType, JAVA_LANG_STRING) &&
        isClassType(rightType, GROOVY_LANG_GSTRING)) {
      return Collections.emptyList();
    }
    else {
      return null;
    }
  }
}
