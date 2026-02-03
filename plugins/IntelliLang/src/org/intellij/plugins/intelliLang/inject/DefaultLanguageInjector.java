// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.injection.general.Injection;
import com.intellij.lang.injection.general.LanguageInjectionContributor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;

final class DefaultLanguageInjector implements LanguageInjectionContributor {

  @Override
  public Injection getInjection(@NotNull PsiElement context) {
    if (!(context instanceof PsiLanguageInjectionHost host) || !host.isValidHost()) return null;

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