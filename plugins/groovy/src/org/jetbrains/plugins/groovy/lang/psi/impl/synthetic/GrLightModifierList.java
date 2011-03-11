/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;

public class GrLightModifierList extends LightElement implements GrModifierList {

  private String[] myModifiers;

  private final PsiElement myParent;

  public GrLightModifierList(@NotNull PsiElement parent, String[] modifiers) {
    super(parent.getManager(), parent.getLanguage());
    myModifiers = modifiers;
    myParent = parent;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  public void addModifier(String modifier) {
    myModifiers = ArrayUtil.append(myModifiers, modifier, ArrayUtil.STRING_ARRAY_FACTORY);
  }

  public void clearModifiers() {
    myModifiers = ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public boolean hasModifierProperty(@NotNull String name){
    return GrModifierListImpl.checkModifierProperty(this, name);
  }

  public boolean hasExplicitModifier(@NotNull String name) {
    return ArrayUtil.contains(name, myModifiers);
  }

  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  @NotNull
  public GrAnnotation[] getAnnotations() {
    return GrAnnotation.EMPTY_ARRAY;
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
    throw new IncorrectOperationException("Method addAnnotation is not yet implemented in " + getClass().getName());
  }

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

  @NotNull
  public PsiElement[] getModifiers() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public boolean hasExplicitVisibilityModifiers() {
    for (String modifier : myModifiers) {
      if (modifier.equals(PsiModifier.PRIVATE) || modifier.equals(PsiModifier.PUBLIC) || modifier.equals(PsiModifier.PROTECTED)) return true;
    }
    return false;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitModifierList(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {

  }
}
