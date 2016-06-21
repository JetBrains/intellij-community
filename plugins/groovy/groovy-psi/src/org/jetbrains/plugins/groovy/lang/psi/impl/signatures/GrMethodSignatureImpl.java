/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignatureVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * Created by Max Medvedev on 14/03/14
 */
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
    PsiType type = getSubstitutor().substitute(PsiUtil.getSmartReturnType(myMethod));
    return myEraseParameterTypes ? TypeConversionUtil.erasure(type) : type;
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
