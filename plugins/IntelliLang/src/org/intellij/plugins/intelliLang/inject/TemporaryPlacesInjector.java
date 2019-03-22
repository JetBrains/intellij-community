// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public final class TemporaryPlacesInjector implements MultiHostInjector {
  public static final Logger LOG = Logger.getInstance(TemporaryPlacesInjector.class);

  @Override
  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(PsiLanguageInjectionHost.class);
  }

  @Override
  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement context) {
    if (!(context instanceof PsiLanguageInjectionHost) || !((PsiLanguageInjectionHost)context).isValidHost()) {
      return;
    }

    PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)context;
    TemporaryPlacesRegistry registry = TemporaryPlacesRegistry.getInstance(host.getProject());

    PsiFile containingFile = context.getContainingFile();
    InjectedLanguage injectedLanguage = registry.getLanguageFor(host, containingFile);
    Language language = injectedLanguage != null ? injectedLanguage.getLanguage() : null;
    if (language == null) return;

    final ElementManipulator<PsiLanguageInjectionHost> manipulator = ElementManipulators.getManipulator(host);
    if (manipulator == null) return;
    List<Trinity<PsiLanguageInjectionHost, InjectedLanguage,TextRange>> trinities =
      Collections.singletonList(Trinity.create(host, injectedLanguage, manipulator.getRangeInElement(host)));
    InjectorUtils.registerInjection(language, trinities, containingFile, registrar);
    InjectorUtils.registerSupport(registry.getLanguageInjectionSupport(), false, context, language);
  }
}
