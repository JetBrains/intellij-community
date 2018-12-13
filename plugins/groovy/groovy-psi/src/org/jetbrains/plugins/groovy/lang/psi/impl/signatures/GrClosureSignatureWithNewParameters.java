// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

public class GrClosureSignatureWithNewParameters implements GrSignature {

  private final GrSignature myDelegate;
  private final GrClosureParameter[] myParams;

  public GrClosureSignatureWithNewParameters(@NotNull GrSignature delegate, @NotNull GrClosureParameter[] newParams) {
    myDelegate = delegate;
    myParams = newParams;
  }

  @NotNull
  @Override
  public PsiSubstitutor getSubstitutor() {
    return myDelegate.getSubstitutor();
  }

  @NotNull
  @Override
  public GrClosureParameter[] getParameters() {
    return myParams;
  }

  @Override
  public int getParameterCount() {
    return myParams.length;
  }

  @Override
  public boolean isVarargs() {
    return GrClosureSignatureUtil.isVarArgsImpl(myParams);
  }

  @Nullable
  @Override
  public PsiType getReturnType() {
    return myDelegate.getReturnType();
  }

  @Override
  public boolean isCurried() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myDelegate.isValid();
  }
}
