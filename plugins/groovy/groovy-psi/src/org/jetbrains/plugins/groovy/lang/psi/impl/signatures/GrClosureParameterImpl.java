// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

class GrClosureParameterImpl implements GrClosureParameter {

  private final PsiParameter myParameter;
  private final PsiSubstitutor mySubstitutor;
  private final boolean myEraseType;

  GrClosureParameterImpl(@NotNull PsiParameter parameter) {
    this(parameter, PsiSubstitutor.EMPTY, false);
  }

  GrClosureParameterImpl(@NotNull PsiParameter parameter, @NotNull PsiSubstitutor substitutor, boolean eraseType) {
    myParameter = parameter;
    mySubstitutor = substitutor;
    myEraseType = eraseType;
  }

  @Nullable
  @Override
  public PsiType getType() {
    PsiType type = mySubstitutor.substitute(myParameter.getType());
    return myEraseType ? TypeConversionUtil.erasure(type, mySubstitutor) : type;
  }

  @Override
  public boolean isOptional() {
    return myParameter instanceof GrParameter && ((GrParameter)myParameter).isOptional();
  }

  @Nullable
  @Override
  public GrExpression getDefaultInitializer() {
    return myParameter instanceof GrParameter ? ((GrParameter)myParameter).getInitializerGroovy() : null;
  }

  @Override
  public boolean isValid() {
    return myParameter.isValid();
  }

  @Nullable
  @Override
  public String getName() {
    return myParameter.getName();
  }
}
