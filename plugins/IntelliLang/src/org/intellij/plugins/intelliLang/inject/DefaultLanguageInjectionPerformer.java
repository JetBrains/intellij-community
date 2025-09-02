// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.injection.general.Injection;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.FallbackInjectionPerformer;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class DefaultLanguageInjectionPerformer implements FallbackInjectionPerformer {
  @Override
  public boolean isPrimary() {
    return false;
  }

  @Override
  public boolean performInjection(@NotNull MultiHostRegistrar registrar,
                                  @NotNull Injection injection,
                                  @NotNull PsiElement context) {
    if (!(context instanceof PsiLanguageInjectionHost host) || !host.isValidHost()) {
      return false;
    }

    PsiFile containingFile = context.getContainingFile();
    InjectedLanguage injectedLanguage = InjectedLanguage.create(injection.getInjectedLanguageId(),
                                                                injection.getPrefix(),
                                                                injection.getSuffix(), false);

    String injectionSupportId = injection.getSupportId();
    LanguageInjectionSupport support = injectionSupportId != null ? InjectorUtils.findInjectionSupport(injectionSupportId) : null;

    Language language = injectedLanguage.getLanguage();
    final ElementManipulator<PsiLanguageInjectionHost> manipulator = ElementManipulators.getManipulator(host);
    if (language == null || manipulator == null) {
      if (injection instanceof BaseInjection) {
        return InjectorUtils.registerInjectionSimple(host, (BaseInjection)injection, support, registrar);
      }
      return false;
    }

    List<InjectorUtils.InjectionInfo> infos =
      List.of(new InjectorUtils.InjectionInfo(host, injectedLanguage, manipulator.getRangeInElement(host)));
    InjectorUtils.registerInjection(
      language, containingFile, infos, registrar, it -> {
        if (support != null) {
          InjectorUtils.registerSupport(it, support, true);
        }
      }
    );
    return true;
  }

  @Override
  public void registerSupportIfNone(PsiElement context, Injection injection) {
    if (LanguageInjectionSupport.INJECTOR_SUPPORT.get(context) != null) return;

    String injectionSupportId = injection.getSupportId();
    LanguageInjectionSupport support = injectionSupportId != null ? InjectorUtils.findInjectionSupport(injectionSupportId) : null;
    if (support == null) return;
    Language language = InjectorUtils.getLanguageByString(injection.getInjectedLanguageId());
    if (language == null) return;
    InjectorUtils.registerSupport(support, false, context, language);
  }
}
