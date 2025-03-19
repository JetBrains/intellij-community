// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

final class TemporaryLanguageInjectionSupport extends AbstractLanguageInjectionSupport {

  @Override
  public @NotNull String getId() {
    return TemporaryPlacesRegistry.SUPPORT_ID;
  }

  @Override
  public boolean isApplicableTo(PsiLanguageInjectionHost host) {
    return true;
  }

  @Override
  public Class<?> @NotNull [] getPatternClasses() {
    return ArrayUtil.EMPTY_CLASS_ARRAY;
  }

  @Override
  public boolean addInjectionInPlace(Language language, PsiLanguageInjectionHost host) {
    TemporaryPlacesRegistry.getInstance(host.getProject()).addHostWithUndo(host, InjectedLanguage.create(language.getID()));
    return true;
  }

  @Override
  public boolean removeInjectionInPlace(PsiLanguageInjectionHost psiElement) {
    return TemporaryPlacesRegistry.getInstance(psiElement.getProject()).removeHostWithUndo(psiElement.getProject(), psiElement);
  }
}
