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

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

/**
 * Created by Max Medvedev on 14/03/14
 */
public class GrMethodSignatureWithErasedTypes extends GrMethodSignatureImpl {
  public GrMethodSignatureWithErasedTypes(@NotNull PsiMethod method) {
    super(method, PsiSubstitutor.EMPTY);
  }

  @Override
  public PsiType getReturnType() {
    return TypeConversionUtil.erasure(super.getReturnType());
  }

  @NotNull
  @Override
  protected GrClosureParameter createClosureParameter(@NotNull PsiParameter parameter) {
    return new GrClosureParameterWithErasedType(parameter);
  }
}
