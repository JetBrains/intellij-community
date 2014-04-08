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

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Max Medvedev on 06/04/14
 */
public abstract class GrTupleTypeWithLazyComponents extends GrTupleType {
  private final AtomicNotNullLazyValue<PsiType[]> myComponents = new AtomicNotNullLazyValue<PsiType[]>() {
    @NotNull
    @Override
    protected PsiType[] compute() {
      return inferComponents();
    }
  };

  protected abstract  PsiType[] inferComponents();

  public GrTupleTypeWithLazyComponents(@NotNull GlobalSearchScope scope, @NotNull JavaPsiFacade facade) {
    super(scope, facade);
  }

  @Override
  public PsiType[] getComponentTypes() {
    return myComponents.getValue();
  }

  @NotNull
  @Override
  public PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    return new GrTupleTypeImpl(getComponentTypes(), myFacade, getResolveScope(), languageLevel);
  }

}
