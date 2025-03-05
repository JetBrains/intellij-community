// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

class GrFunctionalExpressionSignature implements GrSignature {

  private final @NotNull GrFunctionalExpression myExpression;

  GrFunctionalExpressionSignature(@NotNull GrFunctionalExpression expression) {
    myExpression = expression;
  }

  @Override
  public @NotNull PsiSubstitutor getSubstitutor() {
    return PsiSubstitutor.EMPTY;
  }

  @Override
  public GrClosureParameter @NotNull [] getParameters() {
    GrParameter[] parameters = myExpression.getAllParameters();
    return ContainerUtil.map(
      parameters,
      parameter -> new GrClosureParameterImpl(parameter, myExpression)
    ).toArray(GrClosureParameter.EMPTY_ARRAY);
  }

  @Override
  public int getParameterCount() {
    return myExpression.getAllParameters().length;
  }

  @Override
  public boolean isVarargs() {
    GrParameter last = ArrayUtil.getLastElement(myExpression.getAllParameters());
    return last != null && last.getType() instanceof PsiArrayType;
  }

  @Override
  public @Nullable PsiType getReturnType() {
    return myExpression.getReturnType();
  }

  @Override
  public boolean isCurried() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myExpression.isValid();
  }
}
