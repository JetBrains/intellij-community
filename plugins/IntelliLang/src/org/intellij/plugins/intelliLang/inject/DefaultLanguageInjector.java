/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class DefaultLanguageInjector implements MultiHostInjector {

  private final Configuration myInjectionConfiguration;

  public DefaultLanguageInjector(Configuration configuration) {
    myInjectionConfiguration = configuration;
  }

  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(PsiLanguageInjectionHost.class);
  }

  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement host) {
    final Set<String> allIds = myInjectionConfiguration.getAllInjectorIds();
    for (LanguageInjectionSupport support : Extensions.getExtensions(LanguageInjectionSupport.EP_NAME)) {
      if (!allIds.contains(support.getId())) continue;
      if (!support.useDefaultInjector(host)) continue;
      for (BaseInjection injection : myInjectionConfiguration.getInjections(support.getId())) {
        if (injection.acceptsPsiElement(host)) {
          final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
          if (language == null) continue;

          final InjectedLanguage injectedLanguage =
            InjectedLanguage.create(injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix(), false);

          final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list =
            new ArrayList<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>();
          for (TextRange range : injection.getInjectedArea(host)) {
            list.add(Trinity.create((PsiLanguageInjectionHost)host, injectedLanguage, range));
          }
          InjectorUtils.registerInjection(language, list, host.getContainingFile(), registrar);
          break;
        }
      }
    }
  }
}