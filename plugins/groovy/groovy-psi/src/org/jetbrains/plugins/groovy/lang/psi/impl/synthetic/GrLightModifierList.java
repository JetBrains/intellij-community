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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListUtil;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl.NAME_TO_MODIFIER_FLAG_MAP;

public class GrLightModifierList extends LightElement implements GrModifierList {

  private int myModifiers;
  private final List<GrAnnotation> myAnnotations = new ArrayList<>();

  private final PsiElement myParent;

  public GrLightModifierList(@NotNull PsiElement parent) {
    super(parent.getManager(), parent.getLanguage());
    myParent = parent;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public PsiFile getContainingFile() {
    return getParent().getContainingFile();
  }

  public void addModifier(String modifier) {
    int code = NAME_TO_MODIFIER_FLAG_MAP.get(modifier);
    assert code != 0;
    myModifiers |= code;
  }

  public void addModifier(@MagicConstant(flagsFromClass = GrModifierFlags.class) int modifier) {
    myModifiers |= modifier;
  }

  public void removeModifier(@MagicConstant(flagsFromClass = GrModifierFlags.class) int modifier) {
    myModifiers &= ~modifier;
  }

  public void setModifiers(int modifiers) {
    myModifiers = modifiers;
  }

  public void setModifiers(String... modifiers) {
    myModifiers = 0;

    for (String modifier : modifiers) {
      addModifier(modifier);
    }
  }

  @Override
  public int getModifierFlags() {
    return myModifiers;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return GrModifierListUtil.hasModifierProperty(this, name);
  }

  @Override
  public boolean hasExplicitModifier(@NotNull String name) {
    return GrModifierListUtil.hasExplicitModifier(this, name);
  }

  @Override
  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @NotNull
  @Override
  public GrAnnotation[] getRawAnnotations() {
    return getAnnotations();
  }

  @Override
  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  @NotNull
  public GrAnnotation[] getAnnotations() {
    return myAnnotations.toArray(new GrAnnotation[myAnnotations.size()]);
  }

  @Override
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return null;
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    final GrLightAnnotation annotation = new GrLightAnnotation(getManager(), getLanguage(), qualifiedName, this);
    myAnnotations.add(annotation);
    return annotation;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModifierList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "GrModifierList";
  }

  @Override
  public String getText() {
    StringBuilder buffer = new StringBuilder();
    for (GrAnnotation annotation : myAnnotations) {
      buffer.append(annotation.getText());
      buffer.append(' ');
    }

    for (@GrModifier.GrModifierConstant String modifier : GrModifier.GROOVY_MODIFIERS) {
      if (hasExplicitModifier(modifier)) {
        buffer.append(modifier);
        buffer.append(' ');
      }
    }

    if (buffer.length() > 0) {
      buffer.delete(buffer.length() - 1, buffer.length());
    }
    return buffer.toString();
  }

  @Override
  @NotNull
  public PsiElement[] getModifiers() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public PsiElement getModifier(@GrModifier.GrModifierConstant @NotNull @NonNls String name) {
    return null;
  }

  @Override
  public boolean hasExplicitVisibilityModifiers() {
    return GrModifierListUtil.hasExplicitVisibilityModifiers(this);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitModifierList(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {

  }

  public void copyModifiers(@NotNull PsiModifierListOwner modifierOwner) {
    int mod = 0;

    PsiModifierList modifierList = modifierOwner.getModifierList();

    if (modifierList instanceof GrModifierList) {
      mod = ((GrModifierList)modifierList).getModifierFlags();
    }
    else if (modifierList != null) {
      for (String modifier : PsiModifier.MODIFIERS) {
        if (modifierList.hasExplicitModifier(modifier)) {
          mod |= NAME_TO_MODIFIER_FLAG_MAP.get(modifier);
        }
      }
    }

    setModifiers(mod);
  }
}
