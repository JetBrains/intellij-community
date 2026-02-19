// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@SuppressWarnings({"UnusedDeclaration"})
public final class PsiExpressionCategory implements PsiEnhancerCategory{

  public static @Nullable PsiClass getClassType(GrExpression expr) {
    final PsiType type = expr.getType();
    return PsiCategoryUtil.getClassType(type, expr);
  }

  /**
   * @return arguments
   */
  public static Collection<GrExpression> getArguments(GrCall call) {
    final GrArgumentList argumentList = call.getArgumentList();
    if (argumentList != null) {
      return Arrays.asList(argumentList.getExpressionArguments());
    }
    return Collections.emptyList();
  }

}
