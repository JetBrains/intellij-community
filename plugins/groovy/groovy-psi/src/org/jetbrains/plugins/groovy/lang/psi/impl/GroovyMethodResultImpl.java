// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.MethodCandidate;
import org.jetbrains.plugins.groovy.util.NotNullCachedComputableWrapper;

public class GroovyMethodResultImpl extends GroovyResolveResultImpl implements GroovyMethodResult {

  private final @NotNull NotNullComputable<PsiSubstitutor> mySubstitutorComputer;
  private final @Nullable MethodCandidate myCandidate;

  public GroovyMethodResultImpl(@NotNull PsiMethod method,
                                @Nullable PsiElement resolveContext,
                                @Nullable SpreadState spreadState,
                                @NotNull PsiSubstitutor partialSubstitutor,
                                @NotNull NotNullComputable<PsiSubstitutor> substitutorComputer,
                                boolean isAccessible, boolean isStaticsOK) {
    this(method, resolveContext, spreadState, partialSubstitutor, substitutorComputer, null, true, isAccessible, isStaticsOK, true);
  }

  public GroovyMethodResultImpl(@NotNull PsiMethod method,
                                @Nullable PsiElement resolveContext,
                                @Nullable SpreadState spreadState,
                                @NotNull PsiSubstitutor partialSubstitutor,
                                @NotNull NotNullComputable<PsiSubstitutor> substitutorComputer,
                                boolean isAccessible, boolean isStaticsOK, boolean isApplicable) {
    this(method, resolveContext, spreadState, partialSubstitutor, substitutorComputer, null, false, isAccessible, isStaticsOK, isApplicable);
  }

  public GroovyMethodResultImpl(@NotNull PsiMethod method,
                                @Nullable PsiElement resolveContext,
                                @Nullable SpreadState spreadState,
                                @NotNull PsiSubstitutor partialSubstitutor,
                                @NotNull NotNullComputable<PsiSubstitutor> substitutorComputer,
                                @Nullable MethodCandidate candidate,
                                boolean isInvokedOnProperty,
                                boolean isAccessible, boolean isStaticsOk, boolean isApplicable) {
    super(method, resolveContext, spreadState, partialSubstitutor, isAccessible, isStaticsOk, isInvokedOnProperty, isApplicable);
    myCandidate = candidate;
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
  @Override
  public PsiSubstitutor getSubstitutor(boolean infer) {
    return infer ? mySubstitutorComputer.compute() : super.getSubstitutor();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    GroovyMethodResultImpl result = (GroovyMethodResultImpl)o;

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

  @Override
  @Nullable
  public MethodCandidate getCandidate() {
    return myCandidate;
  }
}