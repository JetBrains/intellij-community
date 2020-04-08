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

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GrPsiTypeStub extends PsiType {
  public GrPsiTypeStub() {
    super(PsiAnnotation.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return getCanonicalText();
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return "?";
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return false;
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return null;
  }

  @Nullable
  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @Override
  public PsiType @NotNull [] getSuperTypes() {
    return EMPTY_ARRAY;
  }
}