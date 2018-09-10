// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public class LazyFqnClassType extends GrLiteralClassType {

  private final String myFqn;

  private LazyFqnClassType(@NotNull String fqn,
                           @NotNull LanguageLevel languageLevel,
                           @NotNull GlobalSearchScope scope,
                           @NotNull JavaPsiFacade facade) {
    super(languageLevel, scope, facade);
    myFqn = fqn;
  }

  @NotNull
  @Override
  protected String getJavaClassName() {
    return myFqn;
  }

  @NotNull
  @Override
  public PsiType[] getParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    return new LazyFqnClassType(myFqn, languageLevel, getResolveScope(), myFacade);
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return getJavaClassName();
  }

  @Override
  public boolean isValid() {
    return !myFacade.getProject().isDisposed();
  }

  @NotNull
  @Override
  public PsiClassType rawType() {
    return this;
  }

  @NotNull
  public static PsiClassType getLazyType(@NotNull String fqn,
                                         LanguageLevel languageLevel,
                                         @NotNull GlobalSearchScope scope,
                                         @NotNull JavaPsiFacade facade) {
    return new LazyFqnClassType(fqn, languageLevel, scope, facade);
  }

  public static PsiClassType getLazyType(@NotNull String fqn, @NotNull PsiElement context) {
    return new LazyFqnClassType(fqn, LanguageLevel.JDK_1_5, context.getResolveScope(), JavaPsiFacade.getInstance(context.getProject()));
  }
}
