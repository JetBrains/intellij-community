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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.util.NotNullCachedComputableWrapper;

public class GroovyMethodResult extends GroovyResolveResultImpl {

  private final @NotNull NotNullComputable<PsiSubstitutor> mySubstitutorComputer;

  public GroovyMethodResult(@NotNull PsiMethod method,
                            @Nullable PsiElement resolveContext,
                            @Nullable SpreadState spreadState,
                            @NotNull PsiSubstitutor partialSubstitutor,
                            @NotNull NotNullComputable<PsiSubstitutor> substitutorComputer,
                            boolean isAccessible, boolean isStaticsOK) {
    this(method, resolveContext, spreadState, partialSubstitutor, substitutorComputer, true, isAccessible, isStaticsOK, true);
  }

  public GroovyMethodResult(@NotNull PsiMethod method,
                            @Nullable PsiElement resolveContext,
                            @Nullable SpreadState spreadState,
                            @NotNull PsiSubstitutor partialSubstitutor,
                            @NotNull NotNullComputable<PsiSubstitutor> substitutorComputer,
                            boolean isAccessible, boolean isStaticsOK, boolean isApplicable) {
    this(method, resolveContext, spreadState, partialSubstitutor, substitutorComputer, false, isAccessible, isStaticsOK, isApplicable);
  }

  public GroovyMethodResult(@NotNull PsiMethod method,
                            @Nullable PsiElement resolveContext,
                            @Nullable SpreadState spreadState,
                            @NotNull PsiSubstitutor partialSubstitutor,
                            @NotNull NotNullComputable<PsiSubstitutor> substitutorComputer,
                            boolean isInvokedOnProperty,
                            boolean isAccessible, boolean isStaticsOk, boolean isApplicable) {
    super(method, resolveContext, spreadState, partialSubstitutor, isAccessible, isStaticsOk, isInvokedOnProperty, isApplicable);
    mySubstitutorComputer = new NotNullCachedComputableWrapper<>(() -> {
      PsiSubstitutor substitutor = RecursionManager.doPreventingRecursion(this, false, substitutorComputer);
      return substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
    });
  }

  @NotNull
  @Override
  public PsiMethod getElement() {
    return (PsiMethod)super.getElement();
  }

  @NotNull
  @Override
  public PsiSubstitutor getSubstitutor() {
    return mySubstitutorComputer.compute();
  }

  @NotNull
  public PsiSubstitutor getSubstitutor(boolean infer) {
    return infer ? mySubstitutorComputer.compute() : super.getSubstitutor();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    GroovyMethodResult result = (GroovyMethodResult)o;

    if (!mySubstitutorComputer.equals(result.mySubstitutorComputer)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + mySubstitutorComputer.hashCode();
    return result;
  }

  @TestOnly
  @NotNull
  public NotNullComputable<PsiSubstitutor> getSubstitutorComputer() {
    return mySubstitutorComputer;
  }
}