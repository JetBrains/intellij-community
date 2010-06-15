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

import com.intellij.psi.*;
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
  private static final Set<String>[] SET_INSTANCES = new Set[8 * 4];

  private static final String[] VISABILITY_MODIFIERS = {null, PsiModifier.PUBLIC, PsiModifier.PRIVATE, PsiModifier.PROTECTED};

  private static final int[] MODIFIER_MAP = {0, 1, 2, -1, 3, -1, -1, -1, -1};

  static {
    SET_INSTANCES[0] = Collections.emptySet();
    for (int i = 1; i < 4; i++) {
      SET_INSTANCES[i << 3] = Collections.singleton(VISABILITY_MODIFIERS[i]);
    }

    for (int i = 1; i < 8; i++) {
      int attr = i << 3;

      Set<String> set = new LinkedHashSet<String>();
      if ((attr & Modifier.STATIC) != 0) set.add(PsiModifier.STATIC);
      if ((attr & Modifier.FINAL) != 0) set.add(PsiModifier.FINAL);
      if ((attr & (4 << 3)) != 0) set.add(PsiModifier.ABSTRACT);

      if (set.size() == 1) set = Collections.singleton(set.iterator().next());

      SET_INSTANCES[i] = set;

      for (int k = 1; k < 4; k++) {
        Set<String> setWithModifier = new LinkedHashSet<String>();
        setWithModifier.add(VISABILITY_MODIFIERS[k]);
        setWithModifier.addAll(set);
        assert setWithModifier.size() > 1;

        SET_INSTANCES[(k << 3) + i] = setWithModifier;
      }
    }
  }

  private final Set<String> myModifiers;

  public LightModifierList(PsiManager manager, int modifiers) {
    this(manager, getModifierSet(modifiers));
  }

  public LightModifierList(PsiManager manager, Set<String> modifiers) {
    super(manager, GroovyFileType.GROOVY_LANGUAGE);
    myModifiers = modifiers;
  }

  public static Set<String> getModifierSet(int modifiers) {
    assert (modifiers & ~(Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED | Modifier.FINAL | Modifier.ABSTRACT | Modifier.STATIC)) == 0;

    if ((modifiers & Modifier.ABSTRACT) != 0) {
      modifiers = (modifiers & ~Modifier.ABSTRACT) | (1 << 5);
    }

    int visabilityModifierIndex = MODIFIER_MAP[modifiers & 3];
    if (visabilityModifierIndex != -1) {
      return SET_INSTANCES[(modifiers >> 3) + (visabilityModifierIndex << 3)];
    }

    Set<String> res = new HashSet<String>();
    if ((modifiers & Modifier.PUBLIC) != 0) res.add(PsiModifier.PUBLIC);
    if ((modifiers & Modifier.PRIVATE) != 0) res.add(PsiModifier.PRIVATE);
    if ((modifiers & Modifier.PROTECTED) != 0) res.add(PsiModifier.PROTECTED);

    res.addAll(SET_INSTANCES[modifiers >> 3]);

    return res;
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

    StringBuilder buffer = new StringBuilder();
    for (String modifier : myModifiers) {
      buffer.append(modifier).append(' ');
    }

    buffer.setLength(buffer.length() - 1);

    return buffer.toString();
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
