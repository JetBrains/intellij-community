// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.PsiClassImplUtil;
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
  private final PsiElement myContext;

  GrClosureParameterImpl(@NotNull PsiParameter parameter, @NotNull PsiElement context) {
    this(parameter, PsiSubstitutor.EMPTY, false, context);
  }

  GrClosureParameterImpl(@NotNull PsiParameter parameter,
                         @NotNull PsiSubstitutor substitutor,
                         boolean eraseType,
                         @NotNull PsiElement context) {
    myParameter = parameter;
    mySubstitutor = substitutor;
    myEraseType = eraseType;
    myContext = context;
  }

  @Override
  public @Nullable PsiType getType() {
    PsiType correctType = PsiClassImplUtil.correctType(myParameter.getType(), myContext.getResolveScope());
    PsiType type = mySubstitutor.substitute(correctType);
    return myEraseType ? TypeConversionUtil.erasure(type, mySubstitutor) : type;
  }

  @Override
  public boolean isOptional() {
    return myParameter instanceof GrParameter && ((GrParameter)myParameter).isOptional();
  }

  @Override
  public @Nullable GrExpression getDefaultInitializer() {
    return myParameter instanceof GrParameter ? ((GrParameter)myParameter).getInitializerGroovy() : null;
  }

  @Override
  public boolean isValid() {
    return myContext.isValid() && myParameter.isValid();
  }

  @Override
  public @NotNull String getName() {
    return myParameter.getName();
  }
}
