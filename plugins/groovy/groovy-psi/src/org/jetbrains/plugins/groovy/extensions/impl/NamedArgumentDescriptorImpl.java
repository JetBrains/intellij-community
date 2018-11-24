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
package org.jetbrains.plugins.groovy.extensions.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;

public class NamedArgumentDescriptorImpl extends NamedArgumentDescriptorBase {

  private final @Nullable PsiElement myNavigationElement;
  private final @Nullable PsiSubstitutor mySubstitutor;

  public NamedArgumentDescriptorImpl() {
    this((PsiElement)null);
  }

  public NamedArgumentDescriptorImpl(@Nullable PsiElement navigationElement) {
    this(navigationElement, PsiSubstitutor.EMPTY);
  }

  public NamedArgumentDescriptorImpl(@Nullable PsiElement navigationElement, @Nullable PsiSubstitutor substitutor) {
    super();
    myNavigationElement = navigationElement;
    mySubstitutor = substitutor;
  }

  public NamedArgumentDescriptorImpl(@NotNull Priority priority) {
    this(priority, null, null);
  }

  public NamedArgumentDescriptorImpl(@NotNull Priority priority, @Nullable PsiElement navigationElement) {
    this(priority, navigationElement, PsiSubstitutor.EMPTY);
  }

  public NamedArgumentDescriptorImpl(@NotNull Priority priority,
                                     @Nullable PsiElement navigationElement,
                                     @Nullable PsiSubstitutor substitutor) {
    super(priority);
    myNavigationElement = navigationElement;
    mySubstitutor = substitutor;
  }

  @Nullable
  public PsiElement getNavigationElement() {
    return myNavigationElement;
  }

  @NotNull
  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor == null ? PsiSubstitutor.EMPTY : mySubstitutor;
  }

  @Nullable
  @Override
  public PsiPolyVariantReference createReference(@NotNull GrArgumentLabel label) {
    final PsiElement navigationElement = getNavigationElement();
    if (navigationElement == null) return null;

    return new NamedArgumentReference(label, navigationElement, getSubstitutor());
  }
}
