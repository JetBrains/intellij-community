/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;

public class GroovyMethodResult extends GroovyResolveResultImpl {

  private final @NotNull NotNullComputable<PsiSubstitutor> mySubstitutorComputer;

  public GroovyMethodResult(@NotNull PsiMethod method,
                            @Nullable PsiElement resolveContext,
                            @Nullable SpreadState spreadState,
                            @NotNull PsiSubstitutor substitutor,
                            @NotNull NotNullComputable<PsiSubstitutor> substitutorComputer,
                            boolean isAccessible, boolean staticsOK) {
    super(method, resolveContext, spreadState, substitutor, isAccessible, staticsOK, true, true);
    mySubstitutorComputer = substitutorComputer;
  }

  public GroovyMethodResult(@NotNull PsiMethod element,
                            @Nullable PsiElement resolveContext,
                            @Nullable SpreadState spreadState,
                            @NotNull PsiSubstitutor partialSubstitutor,
                            @NotNull NotNullComputable<PsiSubstitutor> substitutorComputer,
                            boolean isInvokedOnProperty,
                            boolean isAccessible, boolean staticsOK, boolean isApplicable) {
    super(element, resolveContext, spreadState, partialSubstitutor, isAccessible, staticsOK, isInvokedOnProperty, isApplicable);
    mySubstitutorComputer = substitutorComputer;
  }

  public GroovyMethodResult(@NotNull PsiMethod element,
                            @Nullable PsiElement resolveContext,
                            @Nullable SpreadState spreadState,
                            @NotNull PsiSubstitutor partialSubstitutor,
                            @NotNull NotNullComputable<PsiSubstitutor> substitutorComputer,
                            boolean isAccessible, boolean staticsOK, boolean isApplicable) {
    super(element, resolveContext, spreadState, partialSubstitutor, isAccessible, staticsOK, false, isApplicable);
    mySubstitutorComputer = substitutorComputer;
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

  @TestOnly
  @NotNull
  public NotNullComputable<PsiSubstitutor> getSubstitutorComputer() {
    return mySubstitutorComputer;
  }
}