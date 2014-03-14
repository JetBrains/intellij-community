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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignatureVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

/**
 * Created by Max Medvedev on 14/03/14
 */
public class GrClosureSignatureWithNewParameters implements GrClosureSignature {
  private final GrClosureSignature myDelegate;
  private final GrClosureParameter[] myParams;

  public GrClosureSignatureWithNewParameters(@NotNull GrClosureSignature delegate, @NotNull GrClosureParameter[] newParams) {
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
