// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.lang.injection.general.Injection;
import com.intellij.lang.injection.general.LanguageInjectionContributor;
import com.intellij.lang.injection.general.SimpleInjection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
final class TemporaryPlacesInjector implements LanguageInjectionContributor {

  @Override
  public @Nullable Injection getInjection(@NotNull PsiElement context) {
    if (!(context instanceof PsiLanguageInjectionHost host) || !host.isValidHost()) {
      return null;
    }

    TemporaryPlacesRegistry registry = TemporaryPlacesRegistry.getInstance(host.getProject());

    PsiFile containingFile = context.getContainingFile();
    InjectedLanguage injectedLanguage = registry.getLanguageFor(host, containingFile);
    Language language = injectedLanguage != null ? injectedLanguage.getLanguage() : null;
    if (language == null) return null;

    return new SimpleInjection(language,
                               injectedLanguage.getPrefix(),
                               injectedLanguage.getSuffix(),
                               registry.getLanguageInjectionSupport().getId());
  }

}
