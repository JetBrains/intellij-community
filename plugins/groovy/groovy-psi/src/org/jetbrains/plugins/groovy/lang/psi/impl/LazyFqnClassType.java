// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public final class LazyFqnClassType extends GrLiteralClassType {

  private final String myFqn;

  private LazyFqnClassType(@NotNull String fqn,
                           @NotNull LanguageLevel languageLevel,
                           @NotNull GlobalSearchScope scope,
                           @NotNull JavaPsiFacade facade) {
    super(languageLevel, scope, facade);
    myFqn = fqn;
  }

  @Override
  protected @NotNull String getJavaClassName() {
    return myFqn;
  }

  @Override
  public PsiType @NotNull [] getParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  @Override
  public @NotNull PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    return new LazyFqnClassType(myFqn, languageLevel, getResolveScope(), myFacade);
  }

  @Override
  public @NotNull String getInternalCanonicalText() {
    return getJavaClassName();
  }

  @Override
  public boolean isValid() {
    return !myFacade.getProject().isDisposed();
  }

  @Override
  public @NotNull PsiClassType rawType() {
    return this;
  }

  public static @NotNull PsiClassType getLazyType(@NotNull String fqn,
                                                  LanguageLevel languageLevel,
                                                  @NotNull GlobalSearchScope scope,
                                                  @NotNull JavaPsiFacade facade) {
    return new LazyFqnClassType(fqn, languageLevel, scope, facade);
  }

  public static PsiClassType getLazyType(@NotNull String fqn, @NotNull PsiElement context) {
    return new LazyFqnClassType(fqn, LanguageLevel.JDK_1_5, context.getResolveScope(), JavaPsiFacade.getInstance(context.getProject()));
  }
}
