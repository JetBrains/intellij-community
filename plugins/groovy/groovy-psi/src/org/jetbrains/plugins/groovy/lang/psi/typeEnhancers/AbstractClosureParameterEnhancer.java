// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;

public abstract class AbstractClosureParameterEnhancer extends GrVariableEnhancer {
  @Override
  public final PsiType getVariableType(GrVariable variable) {
    if (!(variable instanceof GrParameter)) {
      return null;
    }

    GrFunctionalExpression functionalExpression;
    int paramIndex;

    if (variable instanceof ClosureSyntheticParameter) {
      functionalExpression = ((ClosureSyntheticParameter)variable).getClosure();
      paramIndex = 0;
    }
    else {
      PsiElement eParameterList = variable.getParent();
      if (!(eParameterList instanceof GrParameterList)) return null;

      PsiElement eFunctionalExpression = eParameterList.getParent();
      if (!(eFunctionalExpression instanceof GrFunctionalExpression)) return null;

      functionalExpression = (GrFunctionalExpression)eFunctionalExpression;

      GrParameterList parameterList = (GrParameterList)eParameterList;
      paramIndex = parameterList.getParameterNumber((GrParameter)variable);
    }

    PsiType res = getClosureParameterType(functionalExpression, paramIndex);

    if (res instanceof PsiPrimitiveType) {
      return ((PsiPrimitiveType)res).getBoxedType(functionalExpression);
    }

    return res != null ? unwrapBound(res) : null;
  }

  @Nullable
  private static PsiType unwrapBound(@NotNull PsiType type) {
    if (type instanceof PsiWildcardType) {
      PsiWildcardType wildcard = (PsiWildcardType)type;
      return wildcard.isSuper() ? wildcard.getBound() : type;
    }
    else {
      return type;
    }
  }

  @Nullable
  protected abstract PsiType getClosureParameterType(@NotNull GrFunctionalExpression closure, int index);
}
