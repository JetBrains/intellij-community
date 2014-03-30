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
import com.intellij.xdebugger.impl.DebuggerSupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public class DebuggerConfigurableProvider extends ConfigurableProvider {
  @Override
  public Configurable createConfigurable() {
    final List<DebuggerSettingsPanelProvider> providers = new ArrayList<DebuggerSettingsPanelProvider>();
    final DebuggerSupport[] supports = DebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport support : supports) {
      providers.add(support.getSettingsPanelProvider());
    }

    List<Configurable> configurables = new ArrayList<Configurable>();
    Collections.sort(providers, new Comparator<DebuggerSettingsPanelProvider>() {
      public int compare(final DebuggerSettingsPanelProvider o1, final DebuggerSettingsPanelProvider o2) {
        return o2.getPriority() - o1.getPriority();
      }
    });

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
}
