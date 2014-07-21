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
import com.intellij.util.PlatformUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public class DebuggerConfigurableProvider extends ConfigurableProvider {
  @NotNull
  private static List<DebuggerSettingsPanelProvider> getSortedProviders() {
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
    List<DebuggerSettingsPanelProvider> providers = getSortedProviders();

    List<Configurable> configurables = new ArrayList<Configurable>();
    configurables.add(new DataViewsConfigurable());

    Configurable rootConfigurable = null;
    for (DebuggerSettingsPanelProvider provider : providers) {
      configurables.addAll(provider.getConfigurables());
      final Configurable aRootConfigurable = provider.getRootConfigurable();
      if (aRootConfigurable != null) {
        if (rootConfigurable != null) {
          configurables.add(aRootConfigurable);
        }
        else {
          rootConfigurable = aRootConfigurable;
        }
      }
    }
    if (configurables.isEmpty() && rootConfigurable == null) {
      return null;
    }

    //Perhaps we always should have a root node 'Debugger' with separate nodes for language-specific settings under it.
    //However for AppCode there is only one language which is clearly associated with the product
    //This code should removed when we extract the common debugger settings to the root node.
    if (PlatformUtils.isCidr() && rootConfigurable == null && configurables.size() == 1) {
      rootConfigurable = configurables.get(0);
      configurables = Collections.emptyList();
    }

    return new DebuggerConfigurable(rootConfigurable, configurables);
  }

  @NotNull
  static List<Configurable> getConfigurables(@NotNull XDebuggerSettings.Category category) {
    List<DebuggerSettingsPanelProvider> providers = getSortedProviders();
    if (providers.isEmpty()) {
      return Collections.emptyList();
    }

    List<Configurable> configurables = new SmartList<Configurable>();
    for (DebuggerSettingsPanelProvider provider : providers) {
      configurables.addAll(provider.getConfigurable(category));
    }
    return configurables.isEmpty() ? Collections.<Configurable>emptyList() : configurables;
  }
}
