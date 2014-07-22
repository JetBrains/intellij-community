/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public class DebuggerConfigurableProvider extends ConfigurableProvider {
  @NotNull
  static List<DebuggerSettingsPanelProvider> getSortedProviders() {
    List<DebuggerSettingsPanelProvider> providers = null;
    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      DebuggerSettingsPanelProvider provider = support.getSettingsPanelProvider();
      if (providers == null) {
        providers = new SmartList<DebuggerSettingsPanelProvider>();
      }
      providers.add(provider);
    }

    if (ContainerUtil.isEmpty(providers)) {
      return Collections.emptyList();
    }

    if (providers.size() > 1) {
      Collections.sort(providers, new Comparator<DebuggerSettingsPanelProvider>() {
        @Override
        public int compare(DebuggerSettingsPanelProvider o1, DebuggerSettingsPanelProvider o2) {
          return o2.getPriority() - o1.getPriority();
        }
      });
    }
    return providers;
  }

  @Override
  public Configurable createConfigurable() {
    return new DebuggerConfigurable();
  }

  @NotNull
  static List<Configurable> getConfigurables(@NotNull XDebuggerSettings.Category category) {
    List<DebuggerSettingsPanelProvider> providers = getSortedProviders();
    return providers.isEmpty() ? Collections.<Configurable>emptyList() : getConfigurables(category, providers);
  }

  @NotNull
  static List<Configurable> getConfigurables(@NotNull XDebuggerSettings.Category category, @NotNull List<DebuggerSettingsPanelProvider> providers) {
    List<Configurable> configurables = null;
    for (DebuggerSettingsPanelProvider provider : providers) {
      Collection<? extends Configurable> providerConfigurables = provider.getConfigurable(category);
      if (!providerConfigurables.isEmpty()) {
        if (configurables == null) {
          configurables = new SmartList<Configurable>();
        }
        configurables.addAll(providerConfigurables);
      }
    }
    return ContainerUtil.notNullize(configurables);
  }
}
