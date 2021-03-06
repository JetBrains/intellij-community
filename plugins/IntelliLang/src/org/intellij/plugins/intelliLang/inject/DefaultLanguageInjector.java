// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.injection.general.Injection;
import com.intellij.lang.injection.general.LanguageInjectionContributor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;

public final class DefaultLanguageInjector implements LanguageInjectionContributor {

  @Override
  public Injection getInjection(@NotNull PsiElement context) {
    if (!(context instanceof PsiLanguageInjectionHost) || !((PsiLanguageInjectionHost)context).isValidHost()) return null;
    PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)context;

    for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
      if (!support.isApplicableTo(host)) continue;
      if (!support.useDefaultInjector(host)) continue;

      for (BaseInjection injection : Configuration.getProjectInstance(context.getProject()).getInjections(support.getId())) {
        if (!injection.acceptsPsiElement(host)) continue;
        return injection;
      }
    }
    return null;
  }
}