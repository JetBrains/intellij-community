/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ven
 */
public class LightModifierList extends LightElement implements PsiModifierList {

  private final Set<String> myModifiers;

  public LightModifierList(PsiManager manager, int modifiers) {
    this(manager, RecordUtil.getModifierSet(modifiers));
  }

  public LightModifierList(PsiManager manager, Set<String> modifiers) {
    super(manager, GroovyFileType.GROOVY_LANGUAGE);
    myModifiers = modifiers;
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return myModifiers.contains(name);
  }

  public boolean hasExplicitModifier(@NotNull String name) {
    return myModifiers.contains(name);
  }

  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return null;
  }

  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new UnsupportedOperationException("Method addAnnotation is not yet implemented in " + getClass().getName());
  }

  public String getText() {
    if (myModifiers.size() == 0) return "";
    if (myModifiers.size() == 1) return myModifiers.iterator().next();

    return StringUtil.join(myModifiers, " ");
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitModifierList(this);
    }
  }

  public PsiElement copy() {
    return new LightModifierList(myManager, myModifiers);
  }

  public String toString() {
    return "LightModifierList";
  }

}
