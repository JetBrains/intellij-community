// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GrDivExpressionTypeCalculator extends GrNumericBinaryExpressionTypeCalculator {
  public static final GrDivExpressionTypeCalculator INSTANCE = new GrDivExpressionTypeCalculator();

  @Nullable
  @Override
  protected PsiType inferNumericType(@NotNull PsiType ltype, @NotNull PsiType rtype, PsiElement e) {
    if (GrBinaryExpressionUtil.isFloatOrDouble(ltype, rtype)) {
      return GrBinaryExpressionUtil.createDouble(e);
    }

    return GrBinaryExpressionUtil.createBigDecimal(e);
  }

}
