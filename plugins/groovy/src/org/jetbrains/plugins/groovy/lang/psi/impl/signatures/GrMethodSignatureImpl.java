/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.util.Function;
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

  public GrMethodSignatureImpl(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    myMethod = method;
    mySubstitutor = substitutor;
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
    PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    return ContainerUtil.map(parameters, new Function<PsiParameter, GrClosureParameter>() {
      @Override
      public GrClosureParameter fun(PsiParameter parameter) {
        return createClosureParameter(parameter);
      }
    }, new GrClosureParameter[parameters.length]);
  }

  @NotNull
  protected GrClosureParameter createClosureParameter(@NotNull PsiParameter parameter) {
    return new GrClosureParameterImpl(parameter, getSubstitutor());
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
    return getSubstitutor().substitute(PsiUtil.getSmartReturnType(myMethod));
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
