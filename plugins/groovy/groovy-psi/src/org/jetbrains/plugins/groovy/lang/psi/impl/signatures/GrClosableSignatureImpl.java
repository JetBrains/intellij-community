// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

class GrClosableSignatureImpl implements GrSignature {

  private final GrClosableBlock myBlock;

  GrClosableSignatureImpl(GrClosableBlock block) {
    myBlock = block;
  }

  @NotNull
  @Override
  public PsiSubstitutor getSubstitutor() {
    return PsiSubstitutor.EMPTY;
  }

  @NotNull
  @Override
  public GrClosureParameter[] getParameters() {
    GrParameter[] parameters = myBlock.getAllParameters();

    return ContainerUtil.map(parameters, parameter -> createClosureParameter(parameter), new GrClosureParameter[parameters.length]);
  }

  @NotNull
  protected GrClosureParameter createClosureParameter(@NotNull GrParameter parameter) {
    return new GrClosureParameterImpl(parameter);
  }

  @Override
  public int getParameterCount() {
    return myBlock.getAllParameters().length;
  }

  @Override
  public boolean isVarargs() {
    GrParameter last = ArrayUtil.getLastElement(myBlock.getAllParameters());
    return last != null && last.getType() instanceof PsiArrayType;
  }

  @Nullable
  @Override
  public PsiType getReturnType() {
    return myBlock.getReturnType();
  }

  @Override
  public boolean isCurried() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myBlock.isValid();
  }
}
