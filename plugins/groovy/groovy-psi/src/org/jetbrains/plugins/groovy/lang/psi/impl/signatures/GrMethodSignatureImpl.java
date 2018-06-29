// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignatureVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

class GrMethodSignatureImpl implements GrClosureSignature {

  private final PsiMethod myMethod;
  private final PsiSubstitutor mySubstitutor;
  private final boolean myEraseParameterTypes;

  public GrMethodSignatureImpl(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor, boolean eraseParameterTypes) {
    myMethod = method;
    mySubstitutor = substitutor;
    myEraseParameterTypes = eraseParameterTypes;
  }

  @NotNull
  @Override
  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  @NotNull
  @Override
  public GrClosureParameter[] getParameters() {
    return ContainerUtil.map(
      myMethod.getParameterList().getParameters(),
      (parameter) -> new GrClosureParameterImpl(parameter, mySubstitutor, myEraseParameterTypes),
      GrClosureParameter.EMPTY_ARRAY
    );
  }

  @Override
  public int getParameterCount() {
    return myMethod.getParameterList().getParametersCount();
  }

  @Override
  public boolean isVarargs() {
    return GrClosureSignatureUtil.isVarArgsImpl(getParameters());
  }

  @Override
  public PsiType getReturnType() {
    PsiType type = getReturnTypeInner();
    return myEraseParameterTypes ? TypeConversionUtil.erasure(type) : type;
  }

  private PsiType getReturnTypeInner() {
    PsiSubstitutor substitutor = getSubstitutor();
    if (myMethod.isConstructor()) {
      PsiClass clazz = myMethod.getContainingClass();
      if (clazz == null) return null;
      return GroovyPsiElementFactory.getInstance(myMethod.getProject()).createType(clazz, substitutor);
    }
    else {
      return substitutor.substitute(PsiUtil.getSmartReturnType(myMethod));
    }
  }

  @Override
  public boolean isCurried() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myMethod.isValid() && getSubstitutor().isValid();
  }

  @Nullable
  @Override
  public GrSignature curry(@NotNull PsiType[] args, int position, @NotNull PsiElement context) {
    return GrClosureSignatureUtil.curryImpl(this, args, position, context);
  }

  @Override
  public void accept(@NotNull GrSignatureVisitor visitor) {
    visitor.visitClosureSignature(this);
  }
}
