/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCurriedClosureSignature;

public class GrCurriedClosureSignatureImpl implements GrCurriedClosureSignature {

  private final GrClosureSignature myOriginal;
  private final PsiType[] myArgs;
  private final int myPosition;

  public GrCurriedClosureSignatureImpl(GrClosureSignature original, PsiType[] args, int position) {
    myOriginal = original;
    myArgs = args;
    myPosition = position;
  }

  @Override
  public PsiType getReturnType() {
    return myOriginal.getReturnType();
  }

  @Override
  public PsiType[] getCurriedArgs() {
    return myArgs;
  }

  @Override
  public int getCurriedPosition() {
    return myPosition;
  }

  @Override
  public GrClosureSignature getOriginalSignature() {
    return myOriginal;
  }

  @NotNull
  @Override
  public PsiSubstitutor getSubstitutor() {
    return myOriginal.getSubstitutor();
  }

  @NotNull
  @Override
  public GrClosureParameter[] getParameters() {
    return myOriginal.getParameters();
  }

  @Override
  public int getParameterCount() {
    return myOriginal.getParameterCount();
  }

  @Override
  public boolean isVarargs() {
    return myOriginal.isVarargs();
  }

  @Override
  public GrCurriedClosureSignature curry(PsiType[] args, int position) {
    return new GrCurriedClosureSignatureImpl(this, args, position);
  }

  @Override
  public boolean isValid() {
    return myOriginal.isValid();
  }
}
