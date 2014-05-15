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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Max Medvedev on 12/05/14
 */
public class LazyFqnClassType extends GrLiteralClassType {

  private final String myFqn;

  private LazyFqnClassType(@NotNull String fqn,
                           LanguageLevel languageLevel,
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
  public String getClassName() {
    return StringUtil.getShortName(myFqn);
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
