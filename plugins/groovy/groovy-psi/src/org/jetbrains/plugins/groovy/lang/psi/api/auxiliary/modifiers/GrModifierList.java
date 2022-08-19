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

package org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

public interface GrModifierList extends GroovyPsiElement, PsiModifierList {

  int getModifierFlags();

  PsiElement @NotNull [] getModifiers();

  @Nullable
  PsiElement getModifier(@GrModifierConstant @NotNull @NonNls String name);

  boolean hasExplicitVisibilityModifiers();

  @Override
  GrAnnotation @NotNull [] getAnnotations();

  @Override
  boolean hasModifierProperty(@GrModifierConstant @NotNull @NonNls String name);

  @Override
  boolean hasExplicitModifier(@GrModifierConstant @NotNull @NonNls String name);

  @Override
  void setModifierProperty(@GrModifierConstant @NotNull @NonNls String name, boolean value) throws IncorrectOperationException;

  GrAnnotation @NotNull [] getRawAnnotations();
}
