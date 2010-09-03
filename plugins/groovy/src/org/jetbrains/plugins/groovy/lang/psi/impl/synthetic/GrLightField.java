/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author sergey.evdokimov
 */
public class GrLightField extends GrLightVariable implements PsiField {

  private final PsiClass myContainingClass;

  public GrLightField(@NotNull PsiClass containingClass,
                      PsiModifierList modifierList,
                      PsiManager manager,
                      @NonNls String name,
                      @NonNls @NotNull String type,
                      PsiElement element) {
    super(modifierList, manager, name, type, element);
    myContainingClass = containingClass;
  }

  public GrLightField(@NotNull PsiClass containingClass,
                      PsiModifierList modifierList,
                      PsiManager manager,
                      @NonNls String name,
                      @NotNull PsiType type,
                      PsiElement element) {
    super(modifierList, manager, name, type, element);
    myContainingClass = containingClass;
  }

  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }
}
