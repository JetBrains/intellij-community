// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMirrorElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.light.LightMethod;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.ReentrantLock;

public class GrTraitMethod extends LightMethod implements PsiMirrorElement {
  private final ReentrantLock myLock = new ReentrantLock();
  private boolean isNavigationMethodSet = false;
  public GrTraitMethod(@NotNull PsiClass containingClass,
                       @NotNull PsiMethod method,
                       @NotNull PsiSubstitutor substitutor) {
    super(containingClass.getManager(), method, containingClass, containingClass.getLanguage(), substitutor);
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    try {
      myLock.lock();
      if (!isNavigationMethodSet) {
        isNavigationMethodSet = true;
        setNavigationElement(myMethod);
      }
    } finally {
      myLock.unlock();
    }
    return super.getNavigationElement();
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
