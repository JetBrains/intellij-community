// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.psi.OriginInfoAwareElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMirrorElement;
import com.intellij.psi.impl.light.LightMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Max Medvedev
 */
public class DelegatedMethod extends LightMethod implements PsiMethod, PsiMirrorElement, OriginInfoAwareElement {
  private final PsiMethod myPrototype;

  public DelegatedMethod(@NotNull PsiMethod delegate, @NotNull PsiMethod prototype) {
    super(prototype.getManager(), delegate, delegate.getContainingClass(), delegate.getLanguage());
    myPrototype = prototype;
    setNavigationElement(myPrototype);
  }

  @Override
  public @NotNull PsiMethod getPrototype() {
    return myPrototype;
  }

  @Override
  public @Nullable String getOriginInfo() {
    PsiClass aClass = myPrototype.getContainingClass();
    if (aClass != null && aClass.getName() != null) {
      return "delegates to " + aClass.getName();
    }
    return null;
  }
}
