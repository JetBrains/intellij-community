// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethod;
import org.jetbrains.annotations.NotNull;

public class GrTraitMethod extends LightMethod implements PsiMirrorElement {

  public GrTraitMethod(@NotNull PsiClass containingClass,
                       @NotNull PsiMethod method,
                       @NotNull PsiSubstitutor substitutor) {
    super(containingClass.getManager(), method, containingClass, containingClass.getLanguage(), substitutor);
    setNavigationElement(method);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return !name.equals(PsiModifier.ABSTRACT) && super.hasModifierProperty(name);
  }

  @Override
  public @NotNull PsiMethod getPrototype() {
    return myMethod;
  }
}
