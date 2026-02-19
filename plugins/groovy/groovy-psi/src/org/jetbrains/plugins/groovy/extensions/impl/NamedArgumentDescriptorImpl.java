// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @Nullable PsiElement getNavigationElement() {
    return myNavigationElement;
  }

  public @NotNull PsiSubstitutor getSubstitutor() {
    return mySubstitutor == null ? PsiSubstitutor.EMPTY : mySubstitutor;
  }

  @Override
  public @Nullable PsiPolyVariantReference createReference(@NotNull GrArgumentLabel label) {
    final PsiElement navigationElement = getNavigationElement();
    if (navigationElement == null) return null;

    return new NamedArgumentReference(label, navigationElement, getSubstitutor());
  }
}
