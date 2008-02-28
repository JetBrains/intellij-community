/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Iterator;

/**
 * @author ven
 */
public class LightModifierList extends LightElement implements PsiModifierList {
  private Set<String> myModifiers;

  public LightModifierList(PsiManager manager, LinkedHashSet<String> modifiers){
    super(manager);
    myModifiers = modifiers;
  }

  public boolean hasModifierProperty(@NotNull String name){
    return myModifiers.contains(name);
  }

  public boolean hasExplicitModifier(@NotNull String name) {
    return myModifiers.contains(name);
  }

  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return null;
  }

  public String getText(){
    StringBuffer buffer = new StringBuffer();
    for (Iterator<String> it = myModifiers.iterator(); it.hasNext();) {
      buffer.append(it.next());
      if (it.hasNext()) buffer.append(" ");
    }

    return buffer.toString();
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitModifierList(this);
  }

  public PsiElement copy(){
    return null;
  }

  public String toString(){
    return "LightModifierList";
  }

}