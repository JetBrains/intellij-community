/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import com.intellij.util.NullableFunction;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeType;

public class GrRangeExpressionTypeCalculator implements NullableFunction<GrOperatorExpression, PsiType> {

  public static final GrRangeExpressionTypeCalculator INSTANCE = new GrRangeExpressionTypeCalculator();

  @Override
  public PsiType fun(GrOperatorExpression e) {
    final PsiType ltype = e.getLeftType();
    final PsiType rtype = e.getRightType();

    return new GrRangeType(e.getResolveScope(), JavaPsiFacade.getInstance(e.getProject()), ltype, rtype);
  }
}
