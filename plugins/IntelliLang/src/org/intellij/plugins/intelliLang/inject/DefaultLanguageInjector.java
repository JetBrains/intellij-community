// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class DefaultLanguageInjector implements MultiHostInjector {
  private final Configuration myInjectionConfiguration;
  private final LanguageInjectionSupport[] mySupports;

  public DefaultLanguageInjector(@NotNull Project project) {
    myInjectionConfiguration = Configuration.getProjectInstance(project);
    mySupports = InjectorUtils.getActiveInjectionSupports().toArray(new LanguageInjectionSupport[0]);
  }

  @Override
  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(PsiLanguageInjectionHost.class);
  }

  @Override
  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement context) {
    if (!(context instanceof PsiLanguageInjectionHost) || !((PsiLanguageInjectionHost)context).isValidHost()) return;
    PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)context;

    for (LanguageInjectionSupport support : mySupports) {
      if (!support.isApplicableTo(host)) continue;
      if (!support.useDefaultInjector(host)) continue;

      for (BaseInjection injection : myInjectionConfiguration.getInjections(support.getId())) {
        if (!injection.acceptsPsiElement(host)) continue;
        if (!InjectorUtils.registerInjectionSimple(host, injection, support, registrar)) continue;
        return;
      }
    }
  }
}